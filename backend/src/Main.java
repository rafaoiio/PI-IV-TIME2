import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {

    public static void main(String[] args) throws IOException {

        // Criando o endereço em que o server vai estar vingulado.
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Iniciando server na porta definida
        server.start();

        System.out.println("Servidor iniciado em http://localhost:8080/");
    }
}
