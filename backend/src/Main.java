import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;

import org.bson.Document;

import controller.DashboardHandler;
import controller.PerfilUsuarioHandler;
import controller.RelatoriosHandler;
import controller.UsuariosHandler;

public class Main {

        public static void main(String[] args) throws Exception {

                MongoClient cliente = MongoDBConnection.conectar();

                try {
                        // CONEXÃO COM O BANCO
                        MongoDatabase banco = cliente.getDatabase("desperdicio_alimentos");

                        banco.runCommand(new Document("ping", 1));

                        // COLEÇÕES
                        MongoCollection<Document> usuarios = banco.getCollection("users");

                        MongoCollection<Document> sessoes = banco.getCollection("sessoes");

                        MongoCollection<Document> produtos = banco.getCollection("itens");

                        MongoCollection<Document> lotes = banco.getCollection("lotes");

                        MongoCollection<Document> movimentacoes = banco.getCollection("movimentacoes");

                        MongoCollection<Document> categorias = banco.getCollection("categorias");

                        // ÍNDICES DOS USUÁRIOS E SESSÕES
                        usuarios.createIndex(
                                        Indexes.ascending("email"),
                                        new IndexOptions().unique(true));

                        sessoes.createIndex(
                                        Indexes.ascending("token"),
                                        new IndexOptions().unique(true));

                        sessoes.createIndex(
                                        Indexes.ascending("expiraEm"),
                                        new IndexOptions().expireAfter(
                                                        0L, TimeUnit.SECONDS));

                        // SERVIDOR HTTP
                        HttpServer servidor = HttpServer.create(
                                        new InetSocketAddress("127.0.0.1", 8080),
                                        0);

                        // USUÁRIOS
                        servidor.createContext(
                                        "/api/usuarios",
                                        new UsuariosHandler(usuarios));

                        // LOGIN E SESSÕES
                        LoginHandler loginHandler = new LoginHandler(usuarios, sessoes);

                        servidor.createContext("/api/login", loginHandler);
                        servidor.createContext("/api/logout", loginHandler);
                        servidor.createContext("/api/usuario", loginHandler);

                        // PERFIL DO USUÁRIO AUTENTICADO
                        servidor.createContext(
                                        "/api/perfil",
                                        new PerfilUsuarioHandler(usuarios, sessoes));

                        // DASHBOARD
                        servidor.createContext(
                                        "/api/dashboard/resumo",
                                        new DashboardHandler(produtos, lotes));

                        // RELATÓRIOS
                        RelatoriosHandler relatoriosHandler = new RelatoriosHandler(
                                        produtos,
                                        lotes,
                                        movimentacoes);

                        servidor.createContext(
                                        "/api/relatorios/",
                                        relatoriosHandler);

                        // PRODUTOS E CATEGORIAS
                        ProdutosHandler produtosHandler = new ProdutosHandler(
                                        produtos,
                                        categorias,
                                        lotes,
                                        movimentacoes);

                        servidor.createContext(
                                        "/api/produtos",
                                        produtosHandler);

                        servidor.createContext(
                                        "/api/categorias",
                                        produtosHandler);

                        // LOTES
                        LotesHandler lotesHandler = new LotesHandler(
                                        lotes,
                                        produtos,
                                        movimentacoes);

                        servidor.createContext(
                                        "/api/lotes",
                                        lotesHandler);

                        // PRÓXIMOS VENCIMENTOS
                        servidor.createContext(
                                        "/api/lotes/proximos-vencimento",
                                        new ProximosVencimentoHandler(
                                                        lotes,
                                                        produtos));

                        // MOVIMENTAÇÕES
                        servidor.createContext(
                                        "/api/movimentacoes",
                                        new MovimentacoesHandler(
                                                        cliente,
                                                        lotes,
                                                        movimentacoes));

                        // ENCERRAMENTO SEGURO
                        Runtime.getRuntime().addShutdownHook(
                                        new Thread(() -> {
                                                servidor.stop(0);
                                                cliente.close();
                                        }));

                        // INICIALIZAÇÃO
                        servidor.start();

                        System.out.println(
                                        "Servidor iniciado em http://localhost:8080/");

                        System.out.println(
                                        "MongoDB conectado com sucesso!");

                } catch (Exception erro) {
                        cliente.close();
                        throw erro;
                }
        }
}