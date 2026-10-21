import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;

import org.bson.Document;

public class Main {

    public static void main(String[] args) throws Exception {

        // Conecta ao MongoDB Atlas
        MongoClient cliente =
            MongoDBConnection.conectar();

        try {

            // Seleciona o banco
            MongoDatabase banco =
                cliente.getDatabase(
                    "desperdicio_alimentos"
                );

            // Verifica a conexão
            banco.runCommand(
                new Document("ping", 1)
            );

            // Seleciona a collection users
            MongoCollection<Document> usuarios =
                banco.getCollection("users");

            // Impede e-mails duplicados
            usuarios.createIndex(
                Indexes.ascending("email"),
                new IndexOptions().unique(true)
            );

            // Cria o servidor HTTP local
            HttpServer servidor =
                HttpServer.create(
                    new InetSocketAddress(
                        "127.0.0.1",
                        8080
                    ),
                    0
                );

            // Registra a rota de cadastro
            servidor.createContext(
                "/api/usuarios",
                new UsuariosHandler(usuarios)
            );

            // Colecao de produtos
            MongoCollection<Document> produtos =
                banco.getCollection("itens");

            // Colecao de categorias
            MongoCollection<Document> categorias =
                banco.getCollection("categorias");

            // Colecao de lotes
            MongoCollection<Document> lotes =
                banco.getCollection("lotes");

            // Handler compartilhado dos produtos, categorias e lotes
            ProdutosHandler produtosHandler =
                new ProdutosHandler(produtos, categorias, lotes);

            // Rota de cadastro dos produtos
            servidor.createContext(
                "/api/produtos",
                produtosHandler
            );

            // Rota de consulta das categorias
            servidor.createContext(
                "/api/categorias",
                produtosHandler
            );

            // Fecha recursos quando o servidor parar
            Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {

                    servidor.stop(0);
                    cliente.close();

                })
            );

            // Inicia o servidor
            servidor.start();

            System.out.println(
                "Servidor iniciado em http://localhost:8080/"
            );

            System.out.println(
                "MongoDB conectado com sucesso!"
            );

        } catch (Exception erro) {

            cliente.close();
            throw erro;

        }

    }

}
