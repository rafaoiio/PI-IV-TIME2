
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;

import org.bson.Document;

public class Main {

    public static void main(String[] args) throws Exception {

        // Conecta ao MongoDB Atlas usando a classe existente
        MongoClient cliente = MongoDBConnection.conectar();

        try {

            // Seleciona o banco de dados
            MongoDatabase banco =
                    cliente.getDatabase("desperdicio_alimentos");

            // Verifica a conexao com o MongoDB
            banco.runCommand(new Document("ping", 1));

            // =================================================
            // COLECOES DO BANCO
            // =================================================

            // Colecao de usuarios
            MongoCollection<Document> usuarios =
                    banco.getCollection("users");

            // Impede e-mails duplicados
            usuarios.createIndex(
                    Indexes.ascending("email"),
                    new IndexOptions().unique(true)
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

            // =================================================
            // SERVIDOR HTTP
            // =================================================

            HttpServer servidor = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 8080),
                    0
            );

            // =================================================
            // ROTAS DE USUARIOS
            // =================================================

            servidor.createContext(
                    "/api/usuarios",
                    new UsuariosHandler(usuarios)
            );

            // =================================================
            // ROTAS DE PRODUTOS E CATEGORIAS
            // =================================================

            ProdutosHandler produtosHandler =
                    new ProdutosHandler(produtos, categorias);

            servidor.createContext(
                    "/api/produtos",
                    produtosHandler
            );

            servidor.createContext(
                    "/api/categorias",
                    produtosHandler
            );

            // =================================================
            // ROTA DE PROXIMOS VENCIMENTOS - LAURA
            // =================================================

            servidor.createContext(
                    "/api/lotes/proximos-vencimento",
                    new ProximosVencimentoHandler(
                            lotes,
                            produtos
                    )
            );

            // =================================================
            // ENCERRAMENTO DO SERVIDOR
            // =================================================

            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {

                        servidor.stop(0);
                        cliente.close();

                    })
            );

            // =================================================
            // INICIALIZACAO
            // =================================================

            servidor.start();

            System.out.println(
                    "Servidor iniciado em http://localhost:8080/"
            );

            System.out.println(
                    "MongoDB conectado com sucesso!"
            );

            System.out.println(
                    "API Proximos do Vencimento disponivel em:"
            );

            System.out.println(
                    "http://localhost:8080/api/lotes/proximos-vencimento?dias=7"
            );

        } catch (Exception erro) {

            cliente.close();
            throw erro;

        }
    }
}
