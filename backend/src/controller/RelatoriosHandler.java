package controller;

import service.RelatoriosService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;

import org.bson.Document;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class RelatoriosHandler implements HttpHandler {

    private final RelatoriosService relatoriosService;

    private static final Set<String> ORIGENS = Set.of(
            "http://localhost:5500",
            "http://127.0.0.1:5500");

    public RelatoriosHandler(MongoCollection<Document> itens, MongoCollection<Document> lotes,
            MongoCollection<Document> movimentacoes) {
        this.relatoriosService = new RelatoriosService(itens, lotes, movimentacoes);
    }

    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        String origem = requisicao.getRequestHeaders()
                .getFirst("Origin");

        if (origem != null && !ORIGENS.contains(origem)) {
            responder(requisicao, 403, "Origem não permitida.");
            return;
        }

        if (origem != null) {
            requisicao.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", origem);

            requisicao.getResponseHeaders().set(
                    "Access-Control-Allow-Headers", "Content-Type");

            requisicao.getResponseHeaders().set(
                    "Access-Control-Allow-Methods", "GET, OPTIONS");

            requisicao.getResponseHeaders().set(
                    "Vary", "Origin");
        }

        String metodo = requisicao.getRequestMethod();
        String caminho = requisicao.getRequestURI().getPath();

        Supplier<Document> consulta = switch (caminho) {
            case "/api/relatorios/estoque-por-produto" ->
                relatoriosService::consultarEstoquePorProduto;
            case "/api/relatorios/financeiro-por-produto" ->
                relatoriosService::consultarFinanceiroPorProduto;
            case "/api/relatorios/desperdicios/por-produto" ->
                relatoriosService::consultarDesperdicioPorProduto;
            case "/api/relatorios/desperdicios/por-motivo" ->
                relatoriosService::consultaDesperdicioPorMotivo;
            case "/api/relatorios/desperdicios/por-periodo" ->
                () -> consultarDesperdicioPorPeriodo(requisicao);
            case "/api/relatorios/indicadores" ->
                relatoriosService::consultaIndicadoresGerais;
            case "/api/relatorios/desperdicios/evolucao-mensal" ->
                relatoriosService::consultarEvolucaoMensal;
            default -> null;
        };

        if (consulta == null) {
            responder(requisicao, 404, "Rota não encontrada.");
            return;
        }

        if (metodo.equals("OPTIONS")) {
            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();
            return;
        }

        if (!metodo.equals("GET")) {
            requisicao.getResponseHeaders().set(
                    "Allow", "GET, OPTIONS");

            responder(requisicao, 405, "Método não permitido.");
            return;
        }

        try {
            Document relatorio = consulta.get();
            responderJSON(requisicao, 200, relatorio);
        } catch (IllegalArgumentException erro) {
            responder(requisicao, 400, erro.getMessage());
        } catch (MongoException e) {
            responder(requisicao, 500, "Erro ao acessar o banco de Dados.");
        }
    }

    private Document consultarDesperdicioPorPeriodo(HttpExchange requisicao) {
        String query = requisicao.getRequestURI().getRawQuery();

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Informe as datas de inicío e fim.");
        }

        Map<String, String> parametros = new HashMap<>();

        for (String parte : query.split("&")) {
            String[] par = parte.split("=", 2);

            if (par.length != 2 || !Set.of("inicio", "fim").contains(par[0])
                    || parametros.putIfAbsent(par[0], par[1]) != null) {
                throw new IllegalArgumentException("Parâmetros inválidos");
            }
        }

        LocalDate inicio;
        LocalDate fim;
        try {
            inicio = LocalDate.parse(parametros.getOrDefault("inicio", ""));
            fim = LocalDate.parse(parametros.getOrDefault("fim", ""));
        } catch (DateTimeParseException erro) {
            throw new IllegalArgumentException("As datas devem estar no formato AAAA-MM-DD");
        }

        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à final");
        }

        return relatoriosService.consultaDesperdicioPorPeriodo(inicio, fim);
    }

    private static void responder(
            HttpExchange requisicao,
            int status,
            String mensagem) throws IOException {

        responderJSON(
                requisicao,
                status,
                new Document("mensagem", mensagem));
    }

    private static void responderJSON(
            HttpExchange requisicao,
            int status,
            Document dados) throws IOException {

        byte[] resposta = dados.toJson()
                .getBytes(StandardCharsets.UTF_8);

        requisicao.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");

        requisicao.sendResponseHeaders(status, resposta.length);

        try (OutputStream saida = requisicao.getResponseBody()) {
            saida.write(resposta);
        }
    }
}
