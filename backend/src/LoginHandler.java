
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import static com.mongodb.client.model.Filters.*;

public class LoginHandler implements HttpHandler {
    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> sessoes;
    private final SecureRandom random = new SecureRandom();
    private static final Set<String> ORIGENS = Set.of("http://localhost:5500", "http://127.0.0.1:5500");

    public LoginHandler(MongoCollection<Document> usuarios, MongoCollection<Document> sessoes) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
    }

    @Override
    public void handle(HttpExchange req) throws IOException {
        String origem = req.getRequestHeaders().getFirst("Origin");
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, new Document("mensagem", "Origem não permitida."));
            return;
        }
        if (origem != null) {
            req.getResponseHeaders().set("Access-Control-Allow-Origin", origem);
            req.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            req.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            req.getResponseHeaders().set("Vary", "Origin");
        }
        if (req.getRequestMethod().equals("OPTIONS")) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }
        String rota = req.getRequestURI().getPath();
        try {
            if (rota.equals("/api/login") && req.getRequestMethod().equals("POST")) {
                login(req);
            } else if (rota.equals("/api/logout") && req.getRequestMethod().equals("POST")) {
                String token = token(req);
                if (token != null) sessoes.deleteOne(eq("token", token));
                responder(req, 200, new Document("mensagem", "Sessão encerrada."));
            } else if (rota.equals("/api/usuario") && req.getRequestMethod().equals("GET")) {
                Document usuario = autenticar(req, sessoes, usuarios);
                if (usuario == null) {
                    responder(req, 401, new Document("mensagem", "Não autenticado."));
                    return;
                }
                responder(req, 200, dadosPublicos(usuario));
            } else {
                responder(req, 405, new Document("mensagem", "Operação não permitida."));
            }
        } catch (RuntimeException e) {
            responder(req, 500, new Document("mensagem", "Erro ao acessar o serviço."));
        }
    }

    private void login(HttpExchange req) throws IOException {
        byte[] bytes = req.getRequestBody().readNBytes(8193);
        if (bytes.length > 8192) {
            responder(req, 413, new Document("mensagem", "Requisição muito grande."));
            return;
        }
        Document corpo;
        try {
            corpo = Document.parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            responder(req, 400, new Document("mensagem", "JSON inválido."));
            return;
        }
        String email = corpo.getString("email");
        String senha = corpo.getString("senha");
        if (email == null || senha == null || email.length() > 150 || senha.length() > 1000) {
            responder(req, 400, new Document("mensagem", "Informe email e senha válidos."));
            return;
        }
        Document usuario = usuarios.find(eq("email", email.strip().toLowerCase(Locale.ROOT))).first();
        boolean valido = usuario != null && usuario.getString("senhaHash") != null
                && BCrypt.checkpw(senha, usuario.getString("senhaHash"));
        if (!valido || (usuario.containsKey("ativo") && Boolean.FALSE.equals(usuario.getBoolean("ativo")))) {
            responder(req, 401, new Document("mensagem", "Email ou senha incorretos."));
            return;
        }
        byte[] segredo = new byte[32];
        random.nextBytes(segredo);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(segredo);
        Date expiracao = Date.from(Instant.now().plusSeconds(8 * 3600));
        sessoes.insertOne(new Document("token", token)
                .append("usuarioId", usuario.getObjectId("_id"))
                .append("expiraEm", expiracao)
                .append("criadoEm", new Date()));
        responder(req, 200, new Document("token", token)
                .append("usuario", dadosPublicos(usuario))
                .append("expiraEm", expiracao.toInstant().toString()));
    }

    private static Document dadosPublicos(Document usuario) {
        return new Document("id", usuario.getObjectId("_id").toHexString())
                .append("nome", usuario.getString("nome"))
                .append("email", usuario.getString("email"))
                .append("celular", usuario.getString("celular"));
    }

    public static String token(HttpExchange req) {
        String header = req.getRequestHeaders().getFirst("Authorization");
        return header != null && header.startsWith("Bearer ") && header.length() > 7
                ? header.substring(7).trim() : null;
    }

    public static Document autenticar(HttpExchange req, MongoCollection<Document> sessoes,
                                       MongoCollection<Document> usuarios) {
        String token = token(req);
        if (token == null || token.length() > 200) return null;
        Document sessao = sessoes.find(and(eq("token", token), gt("expiraEm", new Date()))).first();
        if (sessao == null) return null;
        Document usuario = usuarios.find(eq("_id", sessao.getObjectId("usuarioId"))).first();
        if (usuario != null && Boolean.FALSE.equals(usuario.getBoolean("ativo"))) return null;
        return usuario;
    }

    public static void responder(HttpExchange req, int status, Document dados) throws IOException {
        byte[] bytes = dados.toJson().getBytes(StandardCharsets.UTF_8);
        req.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        req.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = req.getResponseBody()) { out.write(bytes); }
    }
}
