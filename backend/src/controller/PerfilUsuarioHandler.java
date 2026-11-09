// Código feito por Lucas David de Sousa RA: 25895152

package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;

import org.bson.Document;

import service.PerfilUsuarioService;
import service.SessaoService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Set;

public class PerfilUsuarioHandler implements HttpHandler {

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    private final SessaoService sessaoService;
    private final PerfilUsuarioService perfilUsuarioService;

    // Construtor
    public PerfilUsuarioHandler(
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.sessaoService = new SessaoService(
            usuarios,
            sessoes
        );

        this.perfilUsuarioService = new PerfilUsuarioService(
            usuarios,
            sessaoService
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
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization"
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "PATCH, OPTIONS"
            );

            requisicao.getResponseHeaders().set(
                "Vary",
                "Origin"
            );
        }

        String metodo = requisicao.getRequestMethod();

        String rota = requisicao
            .getRequestURI()
            .getPath();

        // Verificar as rotas disponíveis
        if (
            !"/api/perfil".equals(rota) &&
            !"/api/perfil/senha".equals(rota)
        ) {

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

        // Permitir somente PATCH
        if (!"PATCH".equals(metodo)) {

            requisicao.getResponseHeaders().set(
                "Allow",
                "PATCH, OPTIONS"
            );

            responder(
                requisicao,
                405,
                "Método não permitido."
            );

            return;
        }

        try {

            // Identificar o usuário autenticado
            Document usuario = sessaoService.autenticar(
                requisicao
            );

            if (usuario == null) {

                responder(
                    requisicao,
                    401,
                    "Sessão inválida ou expirada. Faça login novamente."
                );

                return;
            }

            // Ler o JSON enviado pelo frontend
            Document dados = lerCorpo(requisicao);

            if (dados == null) {

                responder(
                    requisicao,
                    400,
                    "JSON inválido ou maior que 8 KB."
                );

                return;
            }

            Document resposta;

            // Alterar a senha do usuário
            if ("/api/perfil/senha".equals(rota)) {

                resposta = perfilUsuarioService.alterarSenha(
                    usuario,
                    dados
                );

            } else {

                // Atualizar e-mail ou celular
                resposta = perfilUsuarioService.atualizarDados(
                    usuario,
                    dados
                );
            }

            // Retornar sucesso ao frontend
            responderJSON(
                requisicao,
                200,
                resposta
            );

        } catch (IllegalArgumentException erro) {

            // Dados inválidos
            responder(
                requisicao,
                400,
                erro.getMessage()
            );

        } catch (NoSuchElementException erro) {

            // Usuário não encontrado
            responder(
                requisicao,
                404,
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
                    "Não foi possível salvar a alteração."
                );
            }

        } catch (MongoException erro) {

            // Erro ao acessar o MongoDB
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
                "Erro ao processar a solicitação."
            );
        }
    }

    // Ler o corpo da requisição
    private static Document lerCorpo(
        HttpExchange requisicao
    ) throws IOException {

        // Limitar o JSON a 8 KB
        byte[] conteudo = requisicao
            .getRequestBody()
            .readNBytes(8193);

        if (conteudo.length > 8192) {
            return null;
        }

        // Converter o conteúdo para Document
        try {

            return Document.parse(
                new String(
                    conteudo,
                    StandardCharsets.UTF_8
                )
            );

        } catch (RuntimeException erro) {

            return null;
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