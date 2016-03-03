/*
 * Copyright (C) 2016 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.wildfly.kubernetes.configmap;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import okio.BufferedSink;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ConfigMapOperations {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            final OkHttpClient client = new OkHttpClient();
            client.interceptors().add(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));
            Map<String, String> labels = new HashMap<>();
            labels.put("component", "wildfly");
            Path file = new File("/home/ehsavoie/dev/wildfly/core/dist/target/wildfly-core-2.1.0.CR3-SNAPSHOT/standalone/configuration/standalone.xml").toPath();
            String name = "test";
            String namespace = "default";
            ConfigMapOperations ops = new ConfigMapOperations();
            if (ops.configMapExists(client, namespace, name)) {
                ops.deleteConfigMap(client, namespace, name);
            }
            ops.createConfigMap(client, namespace, name, labels, Collections.singleton(file));
            ops.createConfigMap(client,namespace, name, labels, Collections.singleton(file));

            labels.put("status", "experimental");
            ops.updateConfigMap(client, namespace, name, labels, Collections.singleton(file));
            ops.configMapExists(client, namespace, name);
//            ops.deleteConfigMap(client, namespace, name);
        } catch (IOException ex) {
            Logger.getLogger(ConfigMapOperations.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void createConfigMap(OkHttpClient client, String namespace, String name, Map<String, String> labels, Collection<Path> files) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(namespace, null))
                .post(createJsonPayload(namespace, name, labels, files))
                .build();
        Response response = client.newCall(request).execute();
        if (HttpURLConnection.HTTP_CREATED != response.code()) {
            readStatusResponse(response);
        }
    }

    public void deleteConfigMap(OkHttpClient client, String namespace, String name) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(namespace, name))
                .delete()
                .build();
        Response response = client.newCall(request).execute();
        if (HttpURLConnection.HTTP_OK != response.code()) {
            readStatusResponse(response);
        }
    }

    public boolean configMapExists(OkHttpClient client, String namespace, String name) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(namespace, name))
                .get()
                .build();
        return HttpURLConnection.HTTP_OK == client.newCall(request).execute().code();
    }

    public void updateConfigMap(OkHttpClient client, String namespace, String name, Map<String, String> labels, Collection<Path> files) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(namespace, name))
                .put(createJsonPayload(namespace, name, labels, files))
                .build();
        Response response = client.newCall(request).execute();
        if (HttpURLConnection.HTTP_OK != response.code()) {
            readStatusResponse(response);
        }
    }

    private String buildUrl(String namespace, String name) {
        if (name != null && !name.isEmpty()) {
            return String.format("http://localhost:8080/api/v1/namespaces/%s/configmaps/%s", namespace, name);
        }
        return String.format("http://localhost:8080/api/v1/namespaces/%s/configmaps", namespace);
    }

    public RequestBody createJsonPayload(String namespace, String name, Map<String, String> labels, Collection<Path> files) throws IOException {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/json");
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                ModelNode model = new ModelNode().setEmptyObject();
                model.get("kind").set("ConfigMap");
                model.get("apiVersion").set("v1");
                ModelNode metadataNode = model.get("metadata").setEmptyObject();
                metadataNode.get("name").set(name);
                metadataNode.get("namespace").set(namespace);
                if (!labels.isEmpty()) {
                    ModelNode labelsNode = model.get("labels").setEmptyObject();
                        for (Map.Entry<String, String> label : labels.entrySet()) {
                            labelsNode.get(label.getKey()).set(label.getValue());
                        }
                    }
                ModelNode dataNode = model.get("data").setEmptyObject();
                for (Path file : files) {
                    dataNode.get(file.getFileName().toString()).set(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                }
                try (PrintWriter out = new PrintWriter(sink.outputStream(), true)) {
                    model.writeJSONString(out, true);
                }
            }
        };
    }

    public void readStatusResponse(Response response) throws IOException {       
        try (InputStream in = response.body().byteStream()) {
            ModelNode responseNode = ModelNode.fromJSONStream(in);
            if(responseNode.hasDefined("kind") && responseNode.hasDefined("message") && "Status".equals(responseNode.get("kind").asString())) {
                throw new IOException(responseNode.get("message").asString());
            }
        }
    }
}
