
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import service.SessaoService;

import java.io.IOException;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;

import java.time.Instant;

import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;


public class LoginHandler implements HttpHandler {

    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> sessoes;

    // Serviço reutilizável de autenticação
    private final SessaoService sessaoService;

    private final SecureRandom random = new SecureRandom();

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );


    // Construtor

    public LoginHandler(
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.usuarios = usuarios;
        this.sessoes = sessoes;

        this.sessaoService = new SessaoService(
            usuarios,
            sessoes
        );
    }


    // Receber requisições HTTP

    @Override
    public void handle(
        HttpExchange req
    ) throws IOException {

        String origem = req
            .getRequestHeaders()
            .getFirst("Origin");

        if (
            origem != null &&
            !ORIGENS.contains(origem)
        ) {

            responder(
                req,
                403,
                new Document(
                    "mensagem",
                    "Origem não permitida."
                )
            );

            return;
        }


        // Configuração de CORS
        if (origem != null) {

            req.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                origem
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization"
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
            );

            req.getResponseHeaders().set(
                "Vary",
                "Origin"
            );
        }


        // Requisição de pré-verificação
        if (
            req.getRequestMethod().equals("OPTIONS")
        ) {

            req.sendResponseHeaders(204, -1);
            req.close();

            return;
        }


        String rota = req
            .getRequestURI()
            .getPath();


        try {

            // Login

            if (
                rota.equals("/api/login") &&
                req.getRequestMethod().equals("POST")
            ) {

                login(req);


            // Logout

            } else if (
                rota.equals("/api/logout") &&
                req.getRequestMethod().equals("POST")
            ) {

                String token =
                    SessaoService.extrairToken(req);

                if (token != null) {

                    sessoes.deleteOne(
                        eq("token", token)
                    );
                }

                responder(
                    req,
                    200,
                    new Document(
                        "mensagem",
                        "Sessão encerrada."
                    )
                );


            // Consultar usuário autenticado

            } else if (
                rota.equals("/api/usuario") &&
                req.getRequestMethod().equals("GET")
            ) {

                // Agora utiliza o SessaoService.
                Document usuario =
                    sessaoService.autenticar(req);

                if (usuario == null) {

                    responder(
                        req,
                        401,
                        new Document(
                            "mensagem",
                            "Não autenticado."
                        )
                    );

                    return;
                }

                responder(
                    req,
                    200,
                    dadosPublicos(usuario)
                );


            } else {

                responder(
                    req,
                    405,
                    new Document(
                        "mensagem",
                        "Operação não permitida."
                    )
                );
            }

        } catch (RuntimeException erro) {

            responder(
                req,
                500,
                new Document(
                    "mensagem",
                    "Erro ao acessar o serviço."
                )
            );
        }
    }


    // Realizar login

    private void login(
        HttpExchange req
    ) throws IOException {

        byte[] bytes = req
            .getRequestBody()
            .readNBytes(8193);

        if (bytes.length > 8192) {

            responder(
                req,
                413,
                new Document(
                    "mensagem",
                    "Requisição muito grande."
                )
            );

            return;
        }


        Document corpo;

        try {

            corpo = Document.parse(
                new String(
                    bytes,
                    StandardCharsets.UTF_8
                )
            );

        } catch (RuntimeException erro) {

            responder(
                req,
                400,
                new Document(
                    "mensagem",
                    "JSON inválido."
                )
            );

            return;
        }


        String email = corpo.getString("email");
        String senha = corpo.getString("senha");

        if (
            email == null ||
            senha == null ||
            email.length() > 150 ||
            senha.length() > 1000
        ) {

            responder(
                req,
                400,
                new Document(
                    "mensagem",
                    "Informe email e senha válidos."
                )
            );

            return;
        }


        // Consulta o usuário no MongoDB
        Document usuario = usuarios.find(
            eq(
                "email",
                email.strip().toLowerCase(Locale.ROOT)
            )
        ).first();


        // Valida a senha usando BCrypt
        boolean valido =
            usuario != null &&
            usuario.getString("senhaHash") != null &&
            BCrypt.checkpw(
                senha,
                usuario.getString("senhaHash")
            );


        if (
            !valido ||
            (
                usuario.containsKey("ativo") &&
                Boolean.FALSE.equals(
                    usuario.getBoolean("ativo")
                )
            )
        ) {

            responder(
                req,
                401,
                new Document(
                    "mensagem",
                    "Email ou senha incorretos."
                )
            );

            return;
        }


        // Criar token de sessão

        byte[] segredo = new byte[32];

        random.nextBytes(segredo);

        String token = Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(segredo);


        // Sessão válida por 8 horas
        Date expiracao = Date.from(
            Instant.now().plusSeconds(
                8 * 3600
            )
        );


        // Salva a sessão no MongoDB
        sessoes.insertOne(
            new Document("token", token)
                .append(
                    "usuarioId",
                    usuario.getObjectId("_id")
                )
                .append(
                    "expiraEm",
                    expiracao
                )
                .append(
                    "criadoEm",
                    new Date()
                )
        );


        // Retorna token e dados públicos
        responder(
            req,
            200,
            new Document("token", token)
                .append(
                    "usuario",
                    dadosPublicos(usuario)
                )
                .append(
                    "expiraEm",
                    expiracao.toInstant().toString()
                )
        );
    }


    // Dados públicos do usuário

    private static Document dadosPublicos(
        Document usuario
    ) {

        return new Document(
            "id",
            usuario.getObjectId("_id").toHexString()
        )
            .append(
                "nome",
                usuario.getString("nome")
            )
            .append(
                "email",
                usuario.getString("email")
            )
            .append(
                "celular",
                usuario.getString("celular")
            );
    }


    // Enviar respostas HTTP

    public static void responder(
        HttpExchange req,
        int status,
        Document dados
    ) throws IOException {

        byte[] bytes = dados
            .toJson()
            .getBytes(StandardCharsets.UTF_8);

        req.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        req.sendResponseHeaders(
            status,
            bytes.length
        );

        try (
            OutputStream out = req.getResponseBody()
        ) {

            out.write(bytes);
        }
    }
}
