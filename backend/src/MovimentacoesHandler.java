
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MovimentacoesHandler implements HttpHandler {

    private final MongoClient cliente;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> movimentacoes;

    private static final ZoneId FUSO =
            ZoneId.of("America/Sao_Paulo");

    private static final Set<String> ORIGENS = Set.of(
            "http://localhost:5500",
            "http://127.0.0.1:5500"
    );

    private static final Set<String> MOTIVOS = Set.of(
            "consumido",
            "vencimento",
            "contaminado",
            "danificado",
            "outro"
    );

    public MovimentacoesHandler(
            MongoClient cliente,
            MongoCollection<Document> lotes,
            MongoCollection<Document> movimentacoes) {

        this.cliente = cliente;
        this.lotes = lotes;
        this.movimentacoes = movimentacoes;
    }

    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders()
                .getFirst("Origin");

        boolean permitida =
                origem != null && ORIGENS.contains(origem);

        if (permitida) {
            req.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", origem);
            req.getResponseHeaders().set(
                    "Access-Control-Allow-Headers", "Content-Type");
            req.getResponseHeaders().set(
                    "Access-Control-Allow-Methods", "POST, OPTIONS");
            req.getResponseHeaders().set("Vary", "Origin");
        }

        if (origem != null && !permitida) {
            responder(req, 403, "Origem nao permitida.");
            return;
        }

        if (!"/api/movimentacoes".equals(
                req.getRequestURI().getPath())) {

            responder(req, 404, "Rota nao encontrada.");
            return;
        }

        String metodo = req.getRequestMethod();

        if ("OPTIONS".equals(metodo)) {
            if (!permitida) {
                responder(req, 403, "Origem nao permitida.");
                return;
            }

            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        if (!"POST".equals(metodo)) {
            req.getResponseHeaders().set("Allow", "POST, OPTIONS");
            responder(req, 405, "Metodo nao permitido.");
            return;
        }

        registrarSaida(req);
    }

    private void registrarSaida(HttpExchange req)
            throws IOException {

        byte[] conteudo = req.getRequestBody().readNBytes(8193);

        if (conteudo.length > 8192) {
            responder(req, 413, "Requisicao muito grande.");
            return;
        }

        Document dados;
        ObjectId itemId;
        ObjectId loteId;
        BigDecimal quantidade;
        LocalDate data;
        String motivo;
        String observacao;
        String chave;

        try {
            dados = Document.parse(
                    new String(conteudo, StandardCharsets.UTF_8)
            );

            String itemTexto = texto(dados, "itemId");
            String loteTexto = texto(dados, "loteId");

            if (!ObjectId.isValid(itemTexto)
                    || !ObjectId.isValid(loteTexto)) {
                throw new IllegalArgumentException(
                        "Produto ou lote invalido.");
            }

            itemId = new ObjectId(itemTexto);
            loteId = new ObjectId(loteTexto);

            quantidade = new BigDecimal(
                    texto(dados, "quantidade").replace(",", ".")
            );

            if (quantidade.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Quantidade deve ser positiva.");
            }

            // Valida representacao em Decimal128.
            new Decimal128(quantidade);

            data = LocalDate.parse(texto(dados, "dataSaida"));

            if (data.isAfter(LocalDate.now(FUSO))) {
                throw new IllegalArgumentException(
                        "Data futura nao permitida.");
            }

            motivo = texto(dados, "motivo")
                    .toLowerCase(Locale.ROOT);

            if (!MOTIVOS.contains(motivo)) {
                throw new IllegalArgumentException(
                        "Motivo invalido.");
            }

            observacao = texto(dados, "observacao");

            if (observacao.isEmpty()
                    || observacao.length() > 1000) {
                throw new IllegalArgumentException(
                        "Observacao invalida.");
            }

            chave = UUID.fromString(
                    texto(dados, "idempotencyKey")
            ).toString();

        } catch (Exception erro) {
            responder(req, 400,
                    "Dados invalidos: " + erro.getMessage());
            return;
        }

        String tipo;
        String motivoFormatado;

        switch (motivo) {
            case "consumido":
                tipo = "CONSUMO";
                motivoFormatado = "Consumido";
                break;
            case "vencimento":
                tipo = "VENCIMENTO";
                motivoFormatado = "Vencimento";
                break;
            case "contaminado":
                tipo = "AJUSTE_SAIDA";
                motivoFormatado = "Contaminado";
                break;
            case "danificado":
                tipo = "AJUSTE_SAIDA";
                motivoFormatado = "Danificado";
                break;
            default:
                tipo = "AJUSTE_SAIDA";
                motivoFormatado = "Outro";
        }

        Date dataMovimentacao = Date.from(
                data.atStartOfDay(FUSO).toInstant()
        );

        Decimal128 quantidadeDecimal =
                new Decimal128(quantidade);

        Decimal128 quantidadeNegativa =
                new Decimal128(quantidade.negate());

        // Identifica os dados associados a esta chave.
        // Permite detectar uso da mesma chave com dados diferentes.
        String assinatura;

        try {
            Document dadosCanonicos = new Document()
                    .append("itemId", itemId.toHexString())
                    .append("loteId", loteId.toHexString())
                    .append("quantidade",
                            quantidade.stripTrailingZeros()
                                    .toPlainString())
                    .append("dataSaida", data.toString())
                    .append("motivo", motivo)
                    .append("observacao", observacao);

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    dadosCanonicos.toJson()
                            .getBytes(StandardCharsets.UTF_8)
            );

            assinatura = HexFormat.of().formatHex(hash);

        } catch (Exception erro) {
            responder(req, 500,
                    "Falha ao preparar identificacao da saida.");
            return;
        }

        // Se a chave ja existe, nao desconta o estoque.
        try {
            Document existente = movimentacoes.find(
                    Filters.eq("idempotencyKey", chave)
            ).first();

            if (existente != null) {
                responderRepeticao(req, existente, assinatura);
                return;
            }

        } catch (MongoException erro) {
            responder(req, 503,
                    "Nao foi possivel verificar a operacao.");
            return;
        }

        // Baixa do estoque e historico na mesma transacao.
        try (ClientSession sessao = cliente.startSession()) {

            sessao.startTransaction();

            try {
                UpdateResult atualizacao = lotes.updateOne(
                        sessao,
                        Filters.and(
                                Filters.eq("_id", loteId),
                                Filters.eq("itemId", itemId),
                                Filters.gte(
                                        "quantidadeDisponivel",
                                        quantidadeDecimal
                                )
                        ),
                        Updates.inc(
                                "quantidadeDisponivel",
                                quantidadeNegativa
                        )
                );

                if (atualizacao.getModifiedCount() != 1) {
                    sessao.abortTransaction();

                    responder(req, 409,
                            "Lote incorreto, inexistente "
                            + "ou saldo insuficiente.");
                    return;
                }

                Document movimentacao = new Document()
                        .append("loteId", loteId)
                        .append("tipo", tipo)
                        .append("quantidade", quantidadeDecimal)
                        .append("dataMovimentacao", dataMovimentacao)
                        .append("motivo", motivoFormatado)
                        .append("origem", "MANUAL")
                        .append("observacao", observacao)
                        .append("criadoEm", new Date())
                        .append("idempotencyKey", chave)
                        .append("assinaturaOperacao", assinatura);

                movimentacoes.insertOne(sessao, movimentacao);

                sessao.commitTransaction();

                responder(req, 201,
                        "Saida registrada com sucesso!");

            } catch (MongoException erro) {

                // Se o commit teve resultado desconhecido,
                // a consulta posterior verificara a chave.
                // Nunca repetir automaticamente a baixa com nova chave.
                if (sessao.hasActiveTransaction()) {
                    try {
                        sessao.abortTransaction();
                    } catch (Exception ignorado) {
                        // O estado sera confirmado em nova consulta.
                    }
                }

                try {
                    Document existente = movimentacoes.find(
                            Filters.eq("idempotencyKey", chave)
                    ).first();

                    if (existente != null) {
                        responderRepeticao(req, existente, assinatura);
                        return;
                    }
                } catch (MongoException erroConsulta) {
                    System.err.println(
                            "Falha ao consultar operacao: "
                            + erroConsulta.getMessage()
                    );
                }

                System.err.println(
                        "Falha na transacao: " + erro.getMessage()
                );

                responder(req, 503,
                        "Resultado nao confirmado. "
                        + "Tente novamente com a mesma operacao.");
            }

        } catch (Exception erro) {
            System.err.println(
                    "Erro ao processar movimentacao: "
                    + erro.getMessage()
            );

            responder(req, 503,
                    "Nao foi possivel confirmar a operacao.");
        }
    }

    private void responderRepeticao(
            HttpExchange req,
            Document existente,
            String assinatura) throws IOException {

        if (assinatura.equals(
                existente.getString("assinaturaOperacao"))) {

            responder(req, 200,
                    "Saida ja registrada anteriormente. "
                    + "Nenhuma nova baixa foi realizada.");
        } else {
            responder(req, 409,
                    "Esta chave pertence a uma operacao diferente.");
        }
    }

    private static String texto(
            Document dados,
            String campo) {

        Object valor = dados.get(campo);

        return valor instanceof String
                ? ((String) valor).trim()
                : "";
    }

    private static void responder(
            HttpExchange req,
            int status,
            String mensagem) throws IOException {

        Document resposta = new Document(
                "mensagem", mensagem
        );

        byte[] conteudo = resposta.toJson()
                .getBytes(StandardCharsets.UTF_8);

        req.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        req.getResponseHeaders().set(
                "Cache-Control", "no-store"
        );

        req.sendResponseHeaders(status, conteudo.length);

        try (OutputStream saida = req.getResponseBody()) {
            saida.write(conteudo);
        }
    }
}
