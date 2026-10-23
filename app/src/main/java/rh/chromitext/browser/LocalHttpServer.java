package rh.chromitext.browser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Locale;

public class LocalHttpServer {
    private final int port;
    private final File root;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread serverThread;

    public LocalHttpServer(int port, String directory) throws Exception {
        this.port = port;
        this.root = new File(directory).getCanonicalFile();
        if (!root.isDirectory()) throw new IllegalArgumentException("direktori bukan folder");
        serverSocket = new ServerSocket(port, 32, InetAddress.getByName("127.0.0.1"));
        running = true;
        serverThread = new Thread(new Runnable() {
            public void run() { acceptLoop(); }
        }, "Chromitext-LocalServer-" + port);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public int getPort() { return port; }
    public String getRootPath() { return root.getAbsolutePath(); }
    public boolean isRunning() { return running; }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) { }
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket socket = serverSocket.accept();
                Thread client = new Thread(new Runnable() {
                    public void run() { handle(socket); }
                }, "Chromitext-Http-" + port);
                client.setDaemon(true);
                client.start();
            } catch (Exception error) {
                if (running) { }
            }
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(8000);
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = new BufferedOutputStream(socket.getOutputStream());
            String requestLine = readLine(input, 8192);
            if (requestLine == null) { socket.close(); return; }
            String[] pieces = requestLine.split(" ");
            if (pieces.length < 2) { respond(output, 400, "Bad Request", "text/plain", "bad request", true); socket.close(); return; }
            String method = pieces[0].toUpperCase(Locale.US);
            String path = pieces[1];
            String line;
            int headerBytes = 0;
            while ((line = readLine(input, 8192)) != null && line.length() > 0) {
                headerBytes += line.length();
                if (headerBytes > 32768) break;
            }
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                respond(output, 405, "Method Not Allowed", "text/plain", "method not allowed", true);
                socket.close();
                return;
            }
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);
            path = URLDecoder.decode(path, "UTF-8");
            while (path.startsWith("/")) path = path.substring(1);
            if (path.length() == 0) path = "index.html";
            File file = new File(root, path).getCanonicalFile();
            String prefix = root.getCanonicalPath() + File.separator;
            if (!file.getPath().equals(root.getPath()) && !file.getPath().startsWith(prefix)) {
                respond(output, 403, "Forbidden", "text/plain", "forbidden", true);
            } else if (file.isDirectory()) {
                File index = new File(file, "index.html");
                if (index.isFile()) sendFile(output, index, method);
                else respond(output, 403, "Forbidden", "text/plain", "directory listing disabled", true);
            } else if (!file.isFile() || !file.canRead()) {
                respond(output, 404, "Not Found", "text/plain", "not found", true);
            } else {
                sendFile(output, file, method);
            }
            output.flush();
            socket.close();
        } catch (Exception ignored) {
            try { socket.close(); } catch (Exception ignoredAgain) { }
        }
    }

    private void sendFile(OutputStream output, File file, String method) throws Exception {
        long length = file.length();
        String type = mimeType(file.getName());
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + length +
            "\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n";
        output.write(headers.getBytes("ISO-8859-1"));
        if (!"HEAD".equals(method)) {
            FileInputStream input = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            input.close();
        }
    }

    private void respond(OutputStream output, int status, String reason, String type, String body, boolean includeBody) throws Exception {
        byte[] bytes = body.getBytes("UTF-8");
        String header = "HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: " + type + "; charset=UTF-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        output.write(header.getBytes("ISO-8859-1"));
        if (includeBody) output.write(bytes);
    }

    private static String readLine(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') break;
            if (value != '\r') out.write(value);
            if (out.size() >= limit) break;
        }
        if (value == -1 && out.size() == 0) return null;
        return new String(out.toByteArray(), "ISO-8859-1");
    }

    private static String mimeType(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (n.endsWith(".css")) return "text/css; charset=UTF-8";
        if (n.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (n.endsWith(".json")) return "application/json; charset=UTF-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".txt")) return "text/plain; charset=UTF-8";
        if (n.endsWith(".xml")) return "application/xml; charset=UTF-8";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".glb")) return "model/gltf-binary";
        if (n.endsWith(".gltf")) return "model/gltf+json";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
}
