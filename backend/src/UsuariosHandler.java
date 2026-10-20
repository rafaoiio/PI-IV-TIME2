
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;

import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class UsuariosHandler implements HttpHandler {

    private final MongoCollection<Document> usuarios;

    // Origens utilizadas pelo Live Server do VS Code.
    private static final Set<String> ORIGENS =
        Set.of(
            "http://localhost:5500",
            "http://127.0.0.1:5500"
        );

    public UsuariosHandler(
        MongoCollection<Document> usuarios
    ) {

        this.usuarios = usuarios;

    }


    @Override
    public void handle(
        HttpExchange requisicao
    ) throws IOException {

        // Permite requisições do frontend local.
        String origem = requisicao
            .getRequestHeaders()
            .getFirst("Origin");

        boolean origemPermitida =
            origem != null && ORIGENS.contains(origem);

        if (origemPermitida) {

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                origem
            );

            requisicao.getResponseHeaders().set(
                "Vary",
                "Origin"
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type"
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "POST, OPTIONS"
            );

        }

        String metodo = requisicao.getRequestMethod();

        // Responde à verificação CORS do navegador.
        if (metodo.equals("OPTIONS")) {

            if (!origemPermitida) {

                responder(
                    requisicao, 403,
                    "Origem não permitida."
                );

                return;
            }

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();

            return;
        }

        // O cadastro aceita apenas POST.
        if (!metodo.equals("POST")) {

            requisicao.getResponseHeaders().set(
                "Allow", "POST"
            );

            responder(
                requisicao, 405,
                "Método não permitido."
            );

            return;
        }

        cadastrarUsuario(requisicao);

    }


    private void cadastrarUsuario(
        HttpExchange requisicao
    ) throws IOException {

        // Limita o tamanho da requisição.
        byte[] conteudo = requisicao
            .getRequestBody()
            .readNBytes(8193);

        if (conteudo.length > 8192) {

            responder(
                requisicao, 413,
                "Dados enviados são muito grandes."
            );

            return;
        }


        // Converte o JSON recebido.
        Document dados;

        try {

            dados = Document.parse(
                new String(
                    conteudo,
                    StandardCharsets.UTF_8
                )
            );

        } catch (RuntimeException erro) {

            responder(
                requisicao, 400,
                "JSON inválido."
            );

            return;
        }


        // Recupera os campos.
        String nome =
            obterTexto(dados, "nome")
                .trim()
                .replaceAll("\\s+", " ");

        String email =
            obterTexto(dados, "email")
                .trim()
                .toLowerCase(Locale.ROOT);

        String celular =
            obterTexto(dados, "celular")
                .replaceAll("\\D", "");

        String senha =
            obterTexto(dados, "senha");


        // Remove o código do Brasil, se presente.
        if (
            celular.length() == 13 &&
            celular.startsWith("55")
        ) {

            celular = celular.substring(2);

        }


        // Validação do nome completo.
        if (
            nome.length() < 3 ||
            nome.length() > 100 ||
            nome.split(" ").length < 2
        ) {

            responder(
                requisicao, 400,
                "Informe um nome completo válido."
            );

            return;
        }


        // Validação do e-mail.
        if (
            email.length() > 150 ||
            !email.matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
            )
        ) {

            responder(
                requisicao, 400,
                "Informe um e-mail válido."
            );

            return;
        }


        // Validação do celular com DDD.
        if (!celular.matches("\\d{11}")) {

            responder(
                requisicao, 400,
                "Informe um celular válido com DDD."
            );

            return;
        }


        // Validação da senha.
        int tamanhoSenhaBytes =
            senha.getBytes(StandardCharsets.UTF_8).length;

        if (
            senha.length() < 8 ||
            senha.length() > 50 ||
            tamanhoSenhaBytes > 72
        ) {

            responder(
                requisicao, 400,
                "A senha deve ter de 8 a 50 caracteres e no máximo 72 bytes."
            );

            return;
        }


        // Gera o hash BCrypt.
        String senhaHash =
            BCrypt.hashpw(
                senha,
                BCrypt.gensalt(12)
            );


        // Prepara o documento MongoDB.
        Document usuario = new Document()
            .append("nome", nome)
            .append("email", email)
            .append("celular", celular)
            .append("senhaHash", senhaHash)
            .append("criadoEm", new Date());


        try {

            // Salva na collection users.
            usuarios.insertOne(usuario);

            // Retorna apenas dados não sensíveis.
            Document resposta = new Document()
                .append(
                    "mensagem",
                    "Usuário cadastrado com sucesso!"
                )
                .append(
                    "id",
                    usuario.getObjectId("_id").toHexString()
                );

            responderJSON(
                requisicao,
                201,
                resposta
            );

        } catch (MongoWriteException erro) {

            if (
                erro.getError().getCategory()
                    == ErrorCategory.DUPLICATE_KEY
            ) {

                responder(
                    requisicao, 409,
                    "Este e-mail já está cadastrado."
                );

            } else {

                responder(
                    requisicao, 500,
                    "Não foi possível realizar o cadastro."
                );

            }

        } catch (MongoException erro) {

            responder(
                requisicao, 500,
                "Erro ao acessar o banco de dados."
            );

        }

    }


    // Obtém um texto de forma segura.
    private static String obterTexto(
        Document dados,
        String campo
    ) {

        Object valor = dados.get(campo);

        return valor instanceof String
            ? (String) valor
            : "";

    }


    // Resposta de erro em JSON.
    private static void responder(
        HttpExchange requisicao,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            requisicao,
            status,
            new Document("mensagem", mensagem)
        );

    }


    // Envia a resposta HTTP.
    private static void responderJSON(
        HttpExchange requisicao,
        int status,
        Document dados
    ) throws IOException {

        byte[] resposta = dados
            .toJson()
            .getBytes(StandardCharsets.UTF_8);

        requisicao.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        requisicao.sendResponseHeaders(
            status,
            resposta.length
        );

        try (
            OutputStream saida =
                requisicao.getResponseBody()
        ) {

            saida.write(resposta);

        }

    }

}