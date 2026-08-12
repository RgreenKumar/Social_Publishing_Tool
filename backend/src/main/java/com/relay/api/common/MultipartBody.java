package com.relay.api.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MultipartBody {

    public record Part(String name, String filename, String contentType, byte[] data) {
        public static Part text(String name, String value) {
            return new Part(name, null, "text/plain; charset=UTF-8", value.getBytes(StandardCharsets.UTF_8));
        }

        public static Part file(String name, String filename, String contentType, byte[] data) {
            return new Part(name, filename, contentType, data);
        }
    }

    private final String boundary;
    private final byte[] body;

    private MultipartBody(String boundary, byte[] body) {
        this.boundary = boundary;
        this.body = body;
    }

    public String contentTypeHeader() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public byte[] body() {
        return body;
    }

    public static MultipartBody of(List<Part> parts) throws IOException {
        String boundary = "----PostFusion" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Part part : parts) {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            if (part.filename() != null) {
                out.write(("Content-Disposition: form-data; name=\"" + part.name()
                    + "\"; filename=\"" + part.filename() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: " + part.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            } else {
                out.write(("Content-Disposition: form-data; name=\"" + part.name() + "\"\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            }
            out.write(part.data());
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return new MultipartBody(boundary, out.toByteArray());
    }

    public static List<Part> list(Part... parts) {
        List<Part> list = new ArrayList<>();
        for (Part part : parts) {
            if (part != null) {
                list.add(part);
            }
        }
        return list;
    }
}
