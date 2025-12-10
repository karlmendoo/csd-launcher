/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skcraft.concurrency.ProgressObservable;
import lombok.Data;
import lombok.Getter;
import lombok.extern.java.Log;
import okhttp3.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.skcraft.launcher.LauncherUtils.checkInterrupted;
import static org.apache.commons.io.IOUtils.closeQuietly;

@Log
public class HttpRequest implements Closeable, ProgressObservable {

    private static final int READ_BUFFER_SIZE = 1024 * 8;
    
    // Singleton OkHttpClient to enable Connection Pooling
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Request.Builder builder = new Request.Builder();
    private final String method;
    
    @Getter
    private final URL url;
    
    private Response response;
    private InputStream inputStream;
    private RequestBody requestBody;
    private long contentLength = -1;
    private long readBytes = 0;
    
    private PartialDownloadInfo resumeInfo;

    private HttpRequest(String method, URL url) {
        this.method = method;
        this.url = url;
        this.builder.url(url);
    }

    public HttpRequest bodyJson(Object object) throws IOException {
        String json = mapper.writeValueAsString(object);
        this.requestBody = RequestBody.create(json, MediaType.parse("application/json"));
        return this;
    }

    public HttpRequest bodyForm(Form form) {
        this.requestBody = RequestBody.create(form.toString(), MediaType.parse("application/x-www-form-urlencoded"));
        return this;
    }

    public HttpRequest header(String key, String value) {
        builder.header(key, value);
        return this;
    }

    public HttpRequest execute() throws IOException {
        if (response != null) {
            throw new IllegalArgumentException("Connection already executed");
        }

        // Apply method and body
        // If POST but no body set, provide empty body to satisfy OkHttp
        if (requestBody == null && "POST".equalsIgnoreCase(method)) {
            requestBody = RequestBody.create(new byte[0], null);
        }
        
        builder.method(method, requestBody);

        builder.header("User-Agent", "Mozilla/5.0 (Java) SKMCLauncher");
        
        if (resumeInfo != null) {
            builder.header("Range", "bytes=" + resumeInfo.currentLength + "-");
        }

        response = CLIENT.newCall(builder.build()).execute();
        
        if (response.body() != null) {
            contentLength = response.body().contentLength();
            inputStream = response.body().byteStream();
        } else {
            inputStream = new ByteArrayInputStream(new byte[0]);
        }

        return this;
    }

    public HttpRequest expectResponseCode(int... codes) throws IOException {
        int responseCode = getResponseCode();

        for (int code : codes) {
            if (code == responseCode) {
                return this;
            }
        }

        if (resumeInfo != null && responseCode == 206) {
            return this;
        }

        close();
        throw new IOException("Did not get expected response code, got " + responseCode + " for " + url);
    }

    public <E extends Exception> HttpRequest expectResponseCodeOr(int code, HttpFunction<HttpRequest, E> onError)
            throws E, IOException, InterruptedException {
        int responseCode = getResponseCode();

        if (code == responseCode) return this;

        E exc = onError.call(this);
        close();
        throw exc;
    }

    public HttpRequest expectContentType(String... expectedTypes) throws IOException {
        if (response == null) throw new IllegalArgumentException("No connection has been made!");

        String contentType = response.header("Content-Type");
        if (contentType == null) contentType = "";
        
        for (String expectedType : expectedTypes) {
            if (contentType.startsWith(expectedType)) {
                return this;
            }
        }

        close();
        throw new IOException(String.format("Did not get expected content type '%s', instead got '%s'.",
                String.join(" | ", expectedTypes), contentType));
    }

    public int getResponseCode() {
        if (response == null) throw new IllegalArgumentException("No connection has been made");
        return response.code();
    }

    public boolean isSuccessCode() {
        return response != null && response.isSuccessful();
    }

    public boolean isConnected() {
        return response != null;
    }

    public BufferedResponse returnContent() throws IOException, InterruptedException {
        if (inputStream == null) {
            throw new IllegalArgumentException("No input stream available");
        }

        try {
            return new BufferedResponse(response.body().bytes());
        } finally {
            close();
        }
    }

    public HttpRequest saveContent(File file) throws IOException, InterruptedException {
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        boolean shouldAppend = resumeInfo != null && getResponseCode() == 206;

        try {
            file.getParentFile().mkdirs();
            fos = new FileOutputStream(file, shouldAppend);
            bos = new BufferedOutputStream(fos);

            saveContent(bos);
        } finally {
            closeQuietly(bos);
            closeQuietly(fos);
        }

        return this;
    }

    public HttpRequest saveContent(OutputStream out) throws IOException, InterruptedException {
        try {
            byte[] data = new byte[READ_BUFFER_SIZE];
            int len = 0;
            while ((len = inputStream.read(data, 0, READ_BUFFER_SIZE)) >= 0) {
                out.write(data, 0, len);
                readBytes += len;
                checkInterrupted();
            }

            if (contentLength >= 0 && !isResumedRequest() && contentLength != readBytes) {
                throw new IOException(String.format("Connection closed with %d bytes transferred, expected %d",
                        readBytes, contentLength));
            }
        } finally {
            close();
        }

        return this;
    }

    public Optional<PartialDownloadInfo> canRetryPartial() {
        if (response == null) return Optional.empty();

        if ("bytes".equals(response.header("Accept-Ranges"))) {
            return Optional.of(new PartialDownloadInfo(contentLength, readBytes));
        }

        return Optional.empty();
    }

    public HttpRequest setResumeInfo(PartialDownloadInfo info) {
        this.resumeInfo = info;
        return this;
    }

    public boolean isResumedRequest() {
        return resumeInfo != null;
    }

    @Override
    public double getProgress() {
        if (contentLength >= 0) {
            return (double) readBytes / contentLength;
        } else {
            return -1;
        }
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public void close() throws IOException {
        if (response != null) {
            response.close();
        }
        if (inputStream != null) {
            inputStream.close();
        }
    }

    public static HttpRequest get(URL url) {
        return new HttpRequest("GET", url);
    }

    public static HttpRequest post(URL url) {
        return new HttpRequest("POST", url);
    }

    public static URL url(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    // Retained for compatibility with existing code
    public final static class Form {
        public final List<String> elements = new ArrayList<String>();

        private Form() { }

        public Form add(String key, String value) {
            try {
                elements.add(URLEncoder.encode(key, "UTF-8") +
                        "=" + URLEncoder.encode(value, "UTF-8"));
                return this;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (String element : elements) {
                if (first) {
                    first = false;
                } else {
                    builder.append("&");
                }
                builder.append(element);
            }
            return builder.toString();
        }

        public static Form form() {
            return new Form();
        }
    }

    public class BufferedResponse {
        private final byte[] data;

        private BufferedResponse(byte[] data) {
            this.data = data;
        }

        public byte[] asBytes() {
            return data;
        }

        public String asString(String encoding) throws IOException {
            return new String(data, encoding);
        }

        public <T> T asJson(Class<T> cls) throws IOException {
            return mapper.readValue(asString("UTF-8"), cls);
        }

        public <T> T asJson(TypeReference<T> type) throws IOException {
            return mapper.readValue(asString("UTF-8"), type);
        }

        @SuppressWarnings("unchecked")
        public <T> T asXml(Class<T> cls) throws IOException {
            try {
                JAXBContext context = JAXBContext.newInstance(cls);
                Unmarshaller um = context.createUnmarshaller();
                return (T) um.unmarshal(new ByteArrayInputStream(data));
            } catch (JAXBException e) {
                throw new IOException(e);
            }
        }

        public BufferedResponse saveContent(File file) throws IOException, InterruptedException {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
            return this;
        }
    }

    @Data
    public static class PartialDownloadInfo {
        private final long expectedLength;
        private final long currentLength;
    }
}