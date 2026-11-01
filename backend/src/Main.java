
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;

import org.bson.Document;

import controller.DashboardHandler;
import controller.RelatoriosHandler;

public class Main {

    public static void main(String[] args) throws Exception {

        MongoClient cliente = MongoDBConnection.conectar();

        try {

            MongoDatabase banco = cliente.getDatabase(
                    "desperdicio_alimentos");

            banco.runCommand(new Document("ping", 1));

            MongoCollection<Document> usuarios =
                    banco.getCollection("users");

            MongoCollection<Document> sessoes =
                    banco.getCollection("sessoes");

            usuarios.createIndex(
                    Indexes.ascending("email"),
                    new IndexOptions().unique(true));

            sessoes.createIndex(
                    Indexes.ascending("token"),
                    new IndexOptions().unique(true));

            sessoes.createIndex(
                    Indexes.ascending("expiraEm"),
                    new IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS));

            HttpServer servidor = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 8080),
                    0);

            servidor.createContext(
                    "/api/usuarios",
                    new UsuariosHandler(usuarios));

            LoginHandler loginHandler =
                    new LoginHandler(usuarios, sessoes);

            servidor.createContext(
                    "/api/login",
                    loginHandler);

            servidor.createContext(
                    "/api/logout",
                    loginHandler);

            servidor.createContext(
                    "/api/usuario",
                    loginHandler);

            MongoCollection<Document> produtos =
                    banco.getCollection("itens");

            MongoCollection<Document> lotes =
                    banco.getCollection("lotes");

            MongoCollection<Document> movimentacoes =
                    banco.getCollection("movimentacoes");

            servidor.createContext(
                    "/api/dashboard/resumo",
                    new DashboardHandler(produtos, lotes));

            servidor.createContext(
                    "/api/relatorios/estoque-por-produto",
                    new RelatoriosHandler(produtos, lotes));

            MongoCollection<Document> categorias =
                    banco.getCollection("categorias");

            ProdutosHandler produtosHandler =
                    new ProdutosHandler(produtos, categorias, lotes);

            servidor.createContext(
                    "/api/produtos",
                    produtosHandler);

            servidor.createContext(
                    "/api/categorias",
                    produtosHandler);

            LotesHandler lotesHandler =
                    new LotesHandler(lotes, produtos);

            servidor.createContext(
                    "/api/lotes",
                    lotesHandler);

            servidor.createContext(
                    "/api/lotes/proximos-vencimento",
                    new ProximosVencimentoHandler(lotes, produtos));

            servidor.createContext(
                    "/api/movimentacoes",
                    new MovimentacoesHandler(
                            cliente,
                            lotes,
                            movimentacoes));

            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {
                        servidor.stop(0);
                        cliente.close();
                    }));

            servidor.start();

            System.out.println(
                    "Servidor iniciado em http://localhost:8080/");

            System.out.println(
                    "MongoDB conectado com sucesso!");

            System.out.println(
                    "Rotas de cadastro e login disponíveis!");

        } catch (Exception erro) {

            cliente.close();
            throw erro;

        }
    }
}
