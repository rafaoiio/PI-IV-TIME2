// Código feito por Lucas David de Sousa RA: 25895152

package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;

import org.bson.Document;

import service.UsuarioService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class UsuariosHandler implements HttpHandler {

    private final UsuarioService usuarioService;

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // Construtor
    public UsuariosHandler(
        MongoCollection<Document> usuarios
    ) {

        this.usuarioService = new UsuarioService(
            usuarios
        );
    }

    @Override
    public void handle(
        HttpExchange requisicao
    ) throws IOException {

        String origem = requisicao
            .getRequestHeaders()
            .getFirst("Origin");

        // Verificar a origem da requisição
        if (
            origem != null &&
            !ORIGENS.contains(origem)
        ) {

            responder(
                requisicao,
                403,
                "Origem não permitida."
            );

            return;
        }

        // Configurar CORS
        if (origem != null) {

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
        String rota = requisicao.getRequestURI().getPath();

        // Verificar a rota
        if (!"/api/usuarios".equals(rota)) {

            responder(
                requisicao,
                404,
                "Rota não encontrada."
            );

            return;
        }

        // Responder à verificação CORS
        if ("OPTIONS".equals(metodo)) {

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();

            return;
        }

        // Permitir somente POST
        if (!"POST".equals(metodo)) {

            requisicao.getResponseHeaders().set(
                "Allow",
                "POST, OPTIONS"
            );

            responder(
                requisicao,
                405,
                "Método não permitido."
            );

            return;
        }

        cadastrarUsuario(requisicao);
    }

    // Receber os dados do cadastro
    private void cadastrarUsuario(
        HttpExchange requisicao
    ) throws IOException {

        // Limitar o tamanho da requisição
        byte[] conteudo = requisicao
            .getRequestBody()
            .readNBytes(8193);

        if (conteudo.length > 8192) {

            responder(
                requisicao,
                413,
                "Dados enviados são muito grandes."
            );

            return;
        }

        // Converter o JSON recebido
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
                requisicao,
                400,
                "JSON inválido."
            );

            return;
        }

        try {

            // Encaminhar o cadastro para o service
            Document resposta =
                usuarioService.cadastrarUsuario(dados);

            // Retornar sucesso ao frontend
            responderJSON(
                requisicao,
                201,
                resposta
            );

        } catch (IllegalArgumentException erro) {

            // Dados inválidos
            responder(
                requisicao,
                400,
                erro.getMessage()
            );

        } catch (MongoWriteException erro) {

            // Verificar e-mail duplicado
            if (
                erro.getError().getCategory()
                == ErrorCategory.DUPLICATE_KEY
            ) {

                responder(
                    requisicao,
                    409,
                    "Este e-mail já está cadastrado."
                );

            } else {

                responder(
                    requisicao,
                    500,
                    "Não foi possível realizar o cadastro."
                );
            }

        } catch (MongoException erro) {

            // Erro de conexão ou operação no MongoDB
            responder(
                requisicao,
                500,
                "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {

            // Erro inesperado
            responder(
                requisicao,
                500,
                "Erro ao processar o cadastro."
            );
        }
    }

    // Preparar resposta com mensagem
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

    // Enviar resposta JSON
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