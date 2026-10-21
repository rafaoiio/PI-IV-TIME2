
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.ErrorCategory;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;

public class LotesHandler implements HttpHandler {

    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;

    // Origens permitidas para o Live Server
    private static final Set<String> ORIGENS = Set.of(
            "http://localhost:5500",
            "http://127.0.0.1:5500");

    // Construtor
    public LotesHandler(
            MongoCollection<Document> lotes,
            MongoCollection<Document> itens) {
        this.lotes = lotes;
        this.itens = itens;
    }

    // Recebe as requisicoes HTTP
    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        String origem = requisicao.getRequestHeaders()
                .getFirst("Origin");

        boolean origemPermitida = origem != null && ORIGENS.contains(origem);

        // Configura o CORS
        if (origemPermitida) {

            requisicao.getResponseHeaders().set(
                    "Access-Control-Allow-Origin",
                    origem);

            requisicao.getResponseHeaders().set(
                    "Access-Control-Allow-Headers",
                    "Content-Type");

            requisicao.getResponseHeaders().set(
                    "Access-Control-Allow-Methods",
                    "POST, OPTIONS");

            requisicao.getResponseHeaders().set(
                    "Vary",
                    "Origin");
        }

        // Bloqueia origens nao permitidas
        if (origem != null && !origemPermitida) {
            responder(
                    requisicao,
                    403,
                    "Origem nao permitida.");
            return;
        }

        String metodo = requisicao.getRequestMethod();

        // Responde a verificacao CORS
        if (metodo.equals("OPTIONS")) {

            if (!origemPermitida) {
                responder(
                        requisicao,
                        403,
                        "Origem nao permitida.");
                return;
            }

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();
            return;
        }

        // Permite apenas o metodo POST
        if (!metodo.equals("POST")) {

            requisicao.getResponseHeaders().set(
                    "Allow",
                    "POST");

            responder(
                    requisicao,
                    405,
                    "Metodo nao permitido.");
            return;
        }

        // Executa o cadastro do lote
        try {

            cadastrarLote(requisicao);

        } catch (Exception erro) {

            System.err.println(
                    "Erro inesperado ao cadastrar lote:");

            erro.printStackTrace();

            // Tenta responder caso ainda seja possivel
            try {
                responder(
                        requisicao,
                        500,
                        "Erro interno ao cadastrar lote.");
            } catch (IOException | IllegalStateException erroResposta) {
                System.err.println(
                        "Falha ao enviar resposta: "
                                + erroResposta.getMessage());
            }
        }
    }

    // Cadastra um novo lote no MongoDB
    private void cadastrarLote(HttpExchange requisicao)
            throws IOException {

        // Limita o tamanho da requisicao
        byte[] conteudo = requisicao.getRequestBody()
                .readNBytes(8193);

        if (conteudo.length > 8192) {
            responder(
                    requisicao,
                    413,
                    "Dados enviados sao muito grandes.");
            return;
        }

        // Converte o JSON recebido
        Document dados;

        try {

            dados = Document.parse(
                    new String(
                            conteudo,
                            StandardCharsets.UTF_8));

        } catch (RuntimeException erro) {

            responder(
                    requisicao,
                    400,
                    "JSON invalido.");
            return;
        }

        // Recupera os dados do formulario
        String itemIdTexto = obterTexto(dados, "itemId").trim();

        String codigo = obterTexto(dados, "codigo").trim();

        String quantidadeTexto = obterTexto(dados, "quantidadeRecebida").trim();

        String valorTexto = obterTexto(dados, "valorUnitario").trim();

        String entradaTexto = obterTexto(dados, "dataEntrada").trim();

        String validadeTexto = obterTexto(dados, "dataValidade").trim();

        String observacao = obterTexto(dados, "observacao").trim();

        // Valida o identificador do produto
        if (!ObjectId.isValid(itemIdTexto)) {

            responder(
                    requisicao,
                    400,
                    "Selecione um produto valido.");
            return;
        }

        ObjectId itemId = new ObjectId(itemIdTexto);

        // Valida o codigo do lote
        if (codigo.isEmpty() || codigo.length() > 50) {

            responder(
                    requisicao,
                    400,
                    "Informe um codigo de lote valido.");
            return;
        }

        // Converte a quantidade e o valor unitario
        BigDecimal quantidade;
        BigDecimal valorUnitario;

        try {

            quantidade = new BigDecimal(
                    quantidadeTexto.replace(",", "."));

            valorUnitario = new BigDecimal(
                    valorTexto.replace(",", "."));

        } catch (NumberFormatException erro) {

            responder(
                    requisicao,
                    400,
                    "Quantidade ou valor unitario invalido.");
            return;
        }

        // Verifica se a quantidade e positiva
        if (quantidade.signum() <= 0) {

            responder(
                    requisicao,
                    400,
                    "A quantidade recebida deve ser maior que zero.");
            return;
        }

        // Verifica se o valor unitario e positivo
        if (valorUnitario.signum() <= 0) {

            responder(
                    requisicao,
                    400,
                    "O valor unitario deve ser maior que zero.");
            return;
        }

        // Converte e valida as datas
        Date dataEntrada;
        Date dataValidade;

        try {

            LocalDate entrada = LocalDate.parse(entradaTexto);

            LocalDate validade = LocalDate.parse(validadeTexto);

            if (validade.isBefore(entrada)) {

                responder(
                        requisicao,
                        400,
                        "A validade nao pode ser anterior a entrada.");
                return;
            }

            // Converte as datas para o formato Date
            dataEntrada = Date.from(
                    entrada.atStartOfDay()
                            .toInstant(ZoneOffset.UTC));

            dataValidade = Date.from(
                    validade.atStartOfDay()
                            .toInstant(ZoneOffset.UTC));

        } catch (DateTimeParseException erro) {

            responder(
                    requisicao,
                    400,
                    "Informe datas validas.");
            return;
        }

        // Valida a observacao
        if (observacao.isEmpty()
                || observacao.length() > 1000) {

            responder(
                    requisicao,
                    400,
                    "Observacao invalida.");
            return;
        }

        // Calcula o custo total de aquisicao
        BigDecimal custoTotal = quantidade.multiply(valorUnitario);

        try {

            // Verifica se o produto existe
            Document produto = itens.find(
                    eq("_id", itemId)).first();

            if (produto == null) {

                responder(
                        requisicao,
                        404,
                        "Produto nao encontrado no banco.");
                return;
            }

            // Verifica se o codigo do lote ja existe
            Document existente = lotes.find(
                    eq("codigo", codigo)).first();

            if (existente != null) {

                responder(
                        requisicao,
                        409,
                        "Ja existe um lote com este codigo.");
                return;
            }

            // Cria o documento do lote
            Document lote = new Document()
                    .append("codigo", codigo)
                    .append("itemId", itemId)
                    .append(
                            "quantidadeInicial",
                            new Decimal128(quantidade))
                    .append(
                            "quantidadeDisponivel",
                            new Decimal128(quantidade))
                    .append("dataEntrada", dataEntrada)
                    .append("dataValidade", dataValidade)
                    .append(
                            "custoTotalAquisicao",
                            new Decimal128(custoTotal))
                    .append("observacao", observacao)
                    .append("status", false);

            // Salva o lote no MongoDB
            lotes.insertOne(lote);

            System.out.println(
                    "Lote cadastrado: " + codigo);

            // Prepara a resposta de sucesso
            Document resposta = new Document()
                    .append(
                            "mensagem",
                            "Lote cadastrado com sucesso!")
                    .append(
                            "id",
                            lote.getObjectId("_id").toHexString());

            responderJSON(
                    requisicao,
                    201,
                    resposta);

        } catch (MongoWriteException erro) {

            // Trata codigo duplicado quando existe indice unico
            if (erro.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {

                responder(
                        requisicao,
                        409,
                        "Codigo de lote ja cadastrado.");

            } else {

                System.err.println(
                        "Erro ao salvar lote: " + erro.getMessage());

                responder(
                        requisicao,
                        500,
                        "Erro ao salvar lote no MongoDB.");
            }

        } catch (MongoException erro) {

            System.err.println(
                    "Erro no MongoDB: " + erro.getMessage());

            responder(
                    requisicao,
                    500,
                    "Erro ao acessar o banco de dados.");

        } catch (IllegalArgumentException erro) {

            System.err.println(
                    "Erro nos valores do lote: " + erro.getMessage());

            responder(
                    requisicao,
                    400,
                    "Valores numericos invalidos.");

        } catch (Exception erro) {

            System.err.println(
                    "Erro inesperado no cadastro do lote:");

            erro.printStackTrace();

            responder(
                    requisicao,
                    500,
                    "Erro interno ao cadastrar lote.");
        }
    }

    // Recupera um campo de texto do JSON
    private static String obterTexto(
            Document dados,
            String campo) {

        Object valor = dados.get(campo);

        return valor instanceof String
                ? (String) valor
                : "";
    }

    // Prepara uma resposta de erro em JSON
    private static void responder(
            HttpExchange requisicao,
            int status,
            String mensagem) throws IOException {

        responderJSON(
                requisicao,
                status,
                new Document("mensagem", mensagem));
    }

    // Envia a resposta ao frontend
    private static void responderJSON(
            HttpExchange requisicao,
            int status,
            Document dados) throws IOException {

        byte[] resposta = dados.toJson()
                .getBytes(StandardCharsets.UTF_8);

        requisicao.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        requisicao.sendResponseHeaders(
                status,
                resposta.length);

        try (
                OutputStream saida = requisicao.getResponseBody()) {
            saida.write(resposta);
        }
    }
}
