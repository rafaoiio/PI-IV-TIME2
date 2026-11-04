
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
import controller.RelatoriosHandler;

public class Main {

    public static void main(String[] args) throws Exception {

        MongoClient cliente = MongoDBConnection.conectar();

        try {

            // CONEXÃO COM O BANCO
            MongoDatabase banco = cliente.getDatabase(
                    "desperdicio_alimentos");

            banco.runCommand(new Document("ping", 1));

            // COLEÇÕES DE USUÁRIOS
            MongoCollection<Document> usuarios =
                    banco.getCollection("users");

            MongoCollection<Document> sessoes =
                    banco.getCollection("sessoes");

            // ÍNDICES
            usuarios.createIndex(
                    Indexes.ascending("email"),
                    new IndexOptions().unique(true));

            sessoes.createIndex(
                    Indexes.ascending("token"),
                    new IndexOptions().unique(true));

            sessoes.createIndex(
                    Indexes.ascending("expiraEm"),
                    new IndexOptions().expireAfter(
                            0L,
                            TimeUnit.SECONDS));

            // COLEÇÕES DO SISTEMA
            MongoCollection<Document> produtos =
                    banco.getCollection("itens");

            MongoCollection<Document> lotes =
                    banco.getCollection("lotes");

            MongoCollection<Document> movimentacoes =
                    banco.getCollection("movimentacoes");

            MongoCollection<Document> categorias =
                    banco.getCollection("categorias");

            // INICIALIZAÇÃO DO SERVIDOR
            HttpServer servidor = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 8080),
                    0);

            // USUÁRIOS
            servidor.createContext(
                    "/api/usuarios",
                    new UsuariosHandler(usuarios));

            // LOGIN E SESSÃO
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

            // DASHBOARD
            servidor.createContext(
                    "/api/dashboard/resumo",
                    new DashboardHandler(produtos, lotes));

            // RELATÓRIOS
            servidor.createContext(
                    "/api/relatorios/estoque-por-produto",
                    new RelatoriosHandler(produtos, lotes));

            // PRODUTOS
            // Agora recebe também as movimentações.
            ProdutosHandler produtosHandler =
                    new ProdutosHandler(
                            produtos,
                            categorias,
                            lotes,
                            movimentacoes);

            servidor.createContext(
                    "/api/produtos",
                    produtosHandler);

            // CATEGORIAS
            servidor.createContext(
                    "/api/categorias",
                    produtosHandler);

            // LOTES
            LotesHandler lotesHandler =
                    new LotesHandler(lotes, produtos, movimentacoes);

            servidor.createContext(
                    "/api/lotes",
                    lotesHandler);

            // PRÓXIMOS DO VENCIMENTO
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

                // Conecta ao MongoDB Atlas
                MongoClient cliente = MongoDBConnection.conectar();

            servidor.start();

                        // Seleciona o banco
                        MongoDatabase banco = cliente.getDatabase(
                                        "desperdicio_alimentos");

                        // Verifica a conexão
                        banco.runCommand(
                                        new Document("ping", 1));

            System.out.println(
                    "Rotas de cadastro e login disponíveis!");

            System.out.println(
                    "Rotas de produtos, lotes e movimentações disponíveis!");

        } catch (Exception erro) {

                        // Impede e-mails duplicados
                        usuarios.createIndex(
                                        Indexes.ascending("email"),
                                        new IndexOptions().unique(true));

                        // Cria o servidor HTTP local
                        HttpServer servidor = HttpServer.create(
                                        new InetSocketAddress(
                                                        "127.0.0.1",
                                                        8080),
                                        0);

                        // Registra a rota de cadastro
                        servidor.createContext(
                                        "/api/usuarios",
                                        new UsuariosHandler(usuarios));

                        // Colecao de produtos
                        MongoCollection<Document> produtos = banco.getCollection("itens");
                        MongoCollection<Document> lotes = banco.getCollection("lotes");

                        // Colecao de movimentacoes - Tela 7
                        MongoCollection<Document> movimentacoes = banco.getCollection("movimentacoes");

                        // Rota de resumo do Dashboard
                        servidor.createContext(
                                        "/api/dashboard/resumo",
                                        // passo como parâmetro o documento itens que está associado a variável produtos
                                        new DashboardHandler(produtos, lotes));

                        // Rota de relatório de estoque por produto
                        RelatoriosHandler relatorioHandler = new RelatoriosHandler(produtos, lotes, movimentacoes);

                        servidor.createContext("/api/relatorios", relatorioHandler);

                        // Colecao de categorias
                        MongoCollection<Document> categorias = banco.getCollection("categorias");

                        // Handler compartilhado dos produtos, categorias e lotes
                        ProdutosHandler produtosHandler = new ProdutosHandler(produtos, categorias, lotes);

                        // Rota de cadastro dos produtos
                        servidor.createContext(
                                        "/api/produtos",
                                        produtosHandler);

                        // Rota de consulta das categorias
                        servidor.createContext(
                                        "/api/categorias",
                                        produtosHandler);

                        // Handler responsavel pelo cadastro dos lotes
                        LotesHandler lotesHandler = new LotesHandler(lotes, produtos);

                        // Registra a rota de cadastro dos lotes
                        servidor.createContext(
                                        "/api/lotes",
                                        lotesHandler);

                        // Rota de proximos vencimentos - Tela 6
                        servidor.createContext(
                                        "/api/lotes/proximos-vencimento",
                                        new ProximosVencimentoHandler(lotes, produtos));

                        // Rota de registro de saidas e desperdicios - Tela 7
                        servidor.createContext(
                                        "/api/movimentacoes",
                                        new MovimentacoesHandler(
                                                        cliente,
                                                        lotes,
                                                        movimentacoes));

                        // Fecha recursos quando o servidor parar
                        Runtime.getRuntime().addShutdownHook(
                                        new Thread(() -> {

                                                servidor.stop(0);
                                                cliente.close();

                                        }));

                        // Inicia o servidor
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
