package example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;

/** Loopback-only original sample content; no product pack or shader override. */
final class DemoPackHost implements AutoCloseable {
    private final HttpServer server;
    private final ResourcePackRequest request;
    DemoPackHost(Path zip, int port) throws Exception {
        byte[] bytes = Files.readAllBytes(zip);
        String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/npc-demo.zip",exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type","application/zip");
                exchange.sendResponseHeaders(200,bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        request = ResourcePackRequest.resourcePackRequest()
            .packs(ResourcePackInfo.resourcePackInfo(java.util.UUID.nameUUIDFromBytes(sha1.getBytes(java.nio.charset.StandardCharsets.US_ASCII)),URI.create("http://localhost:"+server.getAddress().getPort()+"/npc-demo.zip"),sha1))
            .replace(false).required(false).build();
    }
    ResourcePackRequest request() { return request; }
    @Override public void close() { server.stop(0); }
}
