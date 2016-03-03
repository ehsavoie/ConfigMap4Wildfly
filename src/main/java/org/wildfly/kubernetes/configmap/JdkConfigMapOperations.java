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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class JdkConfigMapOperations {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            System.setProperty("javax.net.debug", "all");
            Map<String, String> labels = new HashMap<>();
            labels.put("component", "wildfly");
            Path file = new File("/home/ehsavoie/dev/wildfly/core/dist/target/wildfly-core-2.1.0.CR3-SNAPSHOT/standalone/configuration/standalone.xml").toPath();
            String name = "test";
            String namespace = "default";
            JdkConfigMapOperations ops = new JdkConfigMapOperations();
            if (ops.configMapExists(namespace, name)) {
                ops.deleteConfigMap(namespace, name);
            }
            boolean works = ops.configMapExists(namespace, name);
//            ops.deleteConfigMap(client, namespace, name);
            ops.createConfigMap(namespace, name, labels, Collections.singleton(file));
//            ops.createConfigMap(namespace, name, labels, Collections.singleton(file));

            labels.put("status", "experimental");
            ops.updateConfigMap(namespace, name, labels, Collections.singleton(file));
            ops.configMapExists(namespace, name);
//            ops.deleteConfigMap(client, namespace, name);
        } catch (IOException ex) {
            Logger.getLogger(JdkConfigMapOperations.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void createConfigMap(String namespace, String name, Map<String, String> labels, Collection<Path> files) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(buildUrl(namespace, null)).openConnection();
        try {
            connection.setAllowUserInteraction(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("content-type", "application/json");
            sendJsonPayload(connection, namespace, name, labels, files);
            connection.connect();            
        } finally {
            if (HttpURLConnection.HTTP_CREATED != connection.getResponseCode()) {
                readStatusResponse(connection);
            }
            connection.disconnect();
        }
    }

    public void deleteConfigMap(String namespace, String name) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(buildUrl(namespace, name)).openConnection();
        try {
            connection.setAllowUserInteraction(false);
            connection.setRequestMethod("DELETE");
            connection.connect();
        } finally {
            if (HttpURLConnection.HTTP_OK != connection.getResponseCode()) {
                readStatusResponse(connection);
            }
            connection.disconnect();
        }
    }

    public boolean configMapExists(String namespace, String name) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(buildUrl(namespace, name)).openConnection();
        try {
            connection.setAllowUserInteraction(false);
            connection.connect();
            return HttpURLConnection.HTTP_OK == connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    public void updateConfigMap(String namespace, String name, Map<String, String> labels, Collection<Path> files) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(buildUrl(namespace, name)).openConnection();
        try {
            connection.setAllowUserInteraction(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("content-type", "application/json");
            sendJsonPayload(connection, namespace, name, labels, files);
            connection.connect();
        } finally {
            if (HttpURLConnection.HTTP_OK != connection.getResponseCode()) {
                readStatusResponse(connection);
            }
            connection.disconnect();
        }
    }

    private String buildUrl(String namespace, String name) {
        if (name != null && !name.isEmpty()) {
            return String.format("http://localhost:8080/api/v1/namespaces/%s/configmaps/%s", namespace, name);
        }
        return String.format("http://localhost:8080/api/v1/namespaces/%s/configmaps", namespace);
    }

    public void sendJsonPayload(HttpURLConnection connection, String namespace, String name, Map<String, String> labels, Collection<Path> files) throws IOException {
        ModelNode model = new ModelNode().setEmptyObject();
        model.get("kind").set("ConfigMap");
        model.get("apiVersion").set("v1");
        ModelNode metadataNode = model.get("metadata").setEmptyObject();
        metadataNode.get("name").set(name);
        metadataNode.get("namespace").set(namespace);
        if (!labels.isEmpty()) {
            ModelNode labelsNode = metadataNode.get("labels").setEmptyObject();
            for (Map.Entry<String, String> label : labels.entrySet()) {
                labelsNode.get(label.getKey()).set(label.getValue());
            }
        }
        ModelNode dataNode = model.get("data").setEmptyObject();
        for (Path file : files) {
            dataNode.get(file.getFileName().toString()).set(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }
        try (PrintWriter out = new PrintWriter(connection.getOutputStream(), true)) {
            model.writeJSONString(out, true);
        }
    }

    public void readStatusResponse(HttpURLConnection connection) throws IOException {
        try (InputStream in = connection.getErrorStream()) {
            ModelNode responseNode = ModelNode.fromJSONStream(in);
            if (responseNode.hasDefined("kind") && responseNode.hasDefined("message") && "Status".equals(responseNode.get("kind").asString())) {
                throw new IOException(responseNode.get("message").asString());
            }
        }
    }
}
