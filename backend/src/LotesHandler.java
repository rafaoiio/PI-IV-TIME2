
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.ErrorCategory;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.*;

public class LotesHandler implements HttpHandler {

    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> movimentacoes;

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // =====================================================
    // CONSTRUTOR
    // =====================================================

    public LotesHandler(
        MongoCollection<Document> lotes,
        MongoCollection<Document> itens,
        MongoCollection<Document> movimentacoes
    ) {
        this.lotes = lotes;
        this.itens = itens;
        this.movimentacoes = movimentacoes;
    }

    // =====================================================
    // ROTAS HTTP
    // =====================================================

    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders()
            .getFirst("Origin");

        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem nao permitida.");
            return;
        }

        if (origem != null) {

            req.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                origem
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type"
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, PATCH, DELETE, OPTIONS"
            );

            req.getResponseHeaders().set(
                "Vary",
                "Origin"
            );
        }

        req.getResponseHeaders().set(
            "Cache-Control",
            "no-store"
        );

        String metodo = req.getRequestMethod();
        String caminho = req.getRequestURI().getPath();

        if (metodo.equals("OPTIONS")) {

            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        try {

            // LISTAR OU CADASTRAR LOTES
            if (caminho.equals("/api/lotes")) {

                switch (metodo) {

                    case "GET":
                        listarLotes(req);
                        break;

                    case "POST":
                        cadastrarLote(req);
                        break;

                    default:
                        responder(
                            req,
                            405,
                            "Metodo nao permitido."
                        );
                }

                return;
            }

            // ROTAS COM ID
            if (caminho.startsWith("/api/lotes/")) {

                String restante = caminho.substring(
                    "/api/lotes/".length()
                );

                String[] partes = restante.split("/", -1);

                if (
                    partes.length < 1 ||
                    partes.length > 2 ||
                    !ObjectId.isValid(partes[0])
                ) {

                    responder(
                        req,
                        400,
                        "ID do lote invalido."
                    );

                    return;
                }

                ObjectId id = new ObjectId(partes[0]);

                // /api/lotes/{id}
                if (partes.length == 1) {

                    switch (metodo) {

                        case "GET":
                            buscarLote(req, id);
                            break;

                        case "PUT":
                            atualizarLote(req, id);
                            break;

                        case "DELETE":
                            excluirLote(req, id);
                            break;

                        default:
                            responder(
                                req,
                                405,
                                "Metodo nao permitido."
                            );
                    }

                    return;
                }

                // VERIFICAR EXCLUSÃO
                if (
                    partes[1].equals("exclusao") &&
                    metodo.equals("GET")
                ) {

                    verificarExclusao(req, id);
                    return;
                }

                // INATIVAR OU REATIVAR
                if (
                    partes[1].equals("status") &&
                    metodo.equals("PATCH")
                ) {

                    alterarStatus(req, id);
                    return;
                }
            }

            responder(
                req,
                404,
                "Rota nao encontrada."
            );

        } catch (IllegalArgumentException erro) {

            responder(
                req,
                400,
                erro.getMessage()
            );

        } catch (MongoWriteException erro) {

            if (
                erro.getError().getCategory() ==
                ErrorCategory.DUPLICATE_KEY
            ) {

                responder(
                    req,
                    409,
                    "Codigo de lote ja cadastrado."
                );

            } else {

                System.err.println(
                    "Erro ao salvar lote: " +
                    erro.getMessage()
                );

                responder(
                    req,
                    500,
                    "Erro ao salvar no MongoDB."
                );
            }

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB: " + erro.getMessage()
            );

            responder(
                req,
                500,
                "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro interno: " + erro.getMessage()
            );

            responder(
                req,
                500,
                "Erro interno do servidor."
            );
        }
    }

    // =====================================================
    // LEITURA DOS DADOS
    // =====================================================

    private Document lerDados(HttpExchange req)
        throws IOException {

        byte[] conteudo = req.getRequestBody()
            .readNBytes(8193);

        if (conteudo.length > 8192) {

            throw new IllegalArgumentException(
                "Dados enviados sao muito grandes."
            );
        }

        try {

            return Document.parse(
                new String(
                    conteudo,
                    StandardCharsets.UTF_8
                )
            );

        } catch (RuntimeException erro) {

            throw new IllegalArgumentException(
                "JSON invalido."
            );
        }
    }

    private static String obterTexto(
        Document dados,
        String campo
    ) {

        Object valor = dados.get(campo);

        return valor instanceof String
            ? ((String) valor).trim()
            : "";
    }

    private static BigDecimal obterDecimal(
        Document dados,
        String campo
    ) {

        Object valor = dados.get(campo);

        if (valor instanceof Decimal128) {

            return ((Decimal128) valor)
                .bigDecimalValue();
        }

        if (valor instanceof Number) {

            return new BigDecimal(
                valor.toString()
            );
        }

        if (valor instanceof String) {

            try {

                return new BigDecimal(
                    (String) valor
                );

            } catch (NumberFormatException erro) {

                return BigDecimal.ZERO;
            }
        }

        return BigDecimal.ZERO;
    }

    private static String dataTexto(Date data) {

        if (data == null) {
            return null;
        }

        return data.toInstant().toString();
    }

    // =====================================================
    // VALIDAR DADOS DO LOTE
    // =====================================================

    // O parametro loteExistenteId permite identificar
    // se estamos cadastrando ou editando um lote.

    private Document validarLote(
        Document dados,
        ObjectId loteExistenteId
    ) {

        String itemTexto =
            obterTexto(dados, "itemId");

        String codigo =
            obterTexto(dados, "codigo");

        String quantidadeTexto =
            obterTexto(dados, "quantidadeRecebida");

        String valorTexto =
            obterTexto(dados, "valorUnitario");

        String entradaTexto =
            obterTexto(dados, "dataEntrada");

        String validadeTexto =
            obterTexto(dados, "dataValidade");

        String observacao =
            obterTexto(dados, "observacao");

        // VALIDA O PRODUTO
        if (!ObjectId.isValid(itemTexto)) {

            throw new IllegalArgumentException(
                "Selecione um produto valido."
            );
        }

        ObjectId itemId = new ObjectId(itemTexto);

        Document produto = itens.find(
            eq("_id", itemId)
        ).first();

        if (produto == null) {

            throw new IllegalArgumentException(
                "Produto nao encontrado."
            );
        }

        // =================================================
        // CORREÇÃO: PRODUTOS INATIVOS
        // =================================================

        // Não permite criar lotes de produtos inativos.
        // Mas permite editar um lote que já pertence
        // ao mesmo produto inativo.

        if (
            "inativo".equals(
                produto.getString("status")
            )
        ) {

            Document loteExistente =
                loteExistenteId == null
                    ? null
                    : lotes.find(
                        eq("_id", loteExistenteId)
                    ).first();

            if (
                loteExistente == null ||
                !itemId.equals(
                    loteExistente.getObjectId("itemId")
                )
            ) {

                throw new IllegalArgumentException(
                    "Nao e permitido vincular lotes " +
                    "a produto inativo."
                );
            }
        }

        // =================================================
        // CÓDIGO DO LOTE
        // =================================================

        if (
            codigo.isEmpty() ||
            codigo.length() > 50
        ) {

            throw new IllegalArgumentException(
                "Codigo de lote invalido."
            );
        }

        // =================================================
        // QUANTIDADE E VALOR
        // =================================================

        BigDecimal quantidade;
        BigDecimal valorUnitario;

        try {

            quantidade = new BigDecimal(
                quantidadeTexto.replace(",", ".")
            );

            valorUnitario = new BigDecimal(
                valorTexto.replace(",", ".")
            );

        } catch (NumberFormatException erro) {

            throw new IllegalArgumentException(
                "Quantidade ou valor unitario invalido."
            );
        }

        if (quantidade.signum() <= 0) {

            throw new IllegalArgumentException(
                "A quantidade recebida deve ser maior que zero."
            );
        }

        if (valorUnitario.signum() <= 0) {

            throw new IllegalArgumentException(
                "O valor unitario deve ser maior que zero."
            );
        }

        // =================================================
        // DATAS
        // =================================================

        Date entrada;
        Date validade;

        try {

            LocalDate dataEntrada =
                LocalDate.parse(entradaTexto);

            LocalDate dataValidade =
                LocalDate.parse(validadeTexto);

            if (
                dataValidade.isBefore(dataEntrada)
            ) {

                throw new IllegalArgumentException(
                    "A validade nao pode ser anterior " +
                    "a data de entrada."
                );
            }

            entrada = Date.from(
                dataEntrada.atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
            );

            validade = Date.from(
                dataValidade.atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
            );

        } catch (DateTimeParseException erro) {

            throw new IllegalArgumentException(
                "Informe datas validas."
            );
        }

        // =================================================
        // OBSERVAÇÃO
        // =================================================

        if (
            observacao.isEmpty() ||
            observacao.length() > 1000
        ) {

            throw new IllegalArgumentException(
                "Observacao invalida."
            );
        }

        // =================================================
        // CUSTO TOTAL
        // =================================================

        BigDecimal custoTotal =
            quantidade.multiply(valorUnitario);

        Decimal128 quantidadeDecimal =
            new Decimal128(quantidade);

        Decimal128 custoDecimal =
            new Decimal128(custoTotal);

        return new Document()
            .append("itemId", itemId)
            .append("codigo", codigo)
            .append(
                "quantidadeInicial",
                quantidadeDecimal
            )
            .append("dataEntrada", entrada)
            .append("dataValidade", validade)
            .append(
                "custoTotalAquisicao",
                custoDecimal
            )
            .append("observacao", observacao);
    }

    // =====================================================
    // CADASTRAR LOTE
    // =====================================================

    private void cadastrarLote(HttpExchange req)
        throws IOException {

        Document dados = lerDados(req);

        // NULL INDICA NOVO CADASTRO
        Document lote = validarLote(
            dados,
            null
        );

        String codigo =
            lote.getString("codigo");

        if (
            lotes.find(
                eq("codigo", codigo)
            ).first() != null
        ) {

            responder(
                req,
                409,
                "Ja existe um lote com este codigo."
            );

            return;
        }

        // Quantidade disponível inicial.
        lote.append(
            "quantidadeDisponivel",
            lote.get("quantidadeInicial")
        );

        // Campo original do banco.
        lote.append("status", false);

        // Campo de inativação.
        lote.append("inativo", false);

        lotes.insertOne(lote);

        responderJSON(
            req,
            201,
            new Document()
                .append(
                    "mensagem",
                    "Lote cadastrado com sucesso!"
                )
                .append(
                    "id",
                    lote.getObjectId("_id").toHexString()
                )
        );
    }

    // =====================================================
    // BUSCAR LOTE PARA EDIÇÃO
    // =====================================================

    private void buscarLote(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document lote = lotes.find(
            eq("_id", id)
        ).first();

        if (lote == null) {

            responder(
                req,
                404,
                "Lote nao encontrado."
            );

            return;
        }

        ObjectId itemId =
            lote.getObjectId("itemId");

        Document resposta = new Document()
            .append(
                "id",
                id.toHexString()
            )
            .append(
                "itemId",
                itemId != null
                    ? itemId.toHexString()
                    : null
            )
            .append(
                "codigo",
                lote.getString("codigo")
            )
            .append(
                "quantidadeInicial",
                obterDecimal(
                    lote,
                    "quantidadeInicial"
                ).toPlainString()
            )
            .append(
                "quantidadeDisponivel",
                obterDecimal(
                    lote,
                    "quantidadeDisponivel"
                ).toPlainString()
            )
            .append(
                "custoTotalAquisicao",
                obterDecimal(
                    lote,
                    "custoTotalAquisicao"
                ).toPlainString()
            )
            .append(
                "dataEntrada",
                dataTexto(
                    lote.getDate("dataEntrada")
                )
            )
            .append(
                "dataValidade",
                dataTexto(
                    lote.getDate("dataValidade")
                )
            )
            .append(
                "observacao",
                lote.getString("observacao")
            )
            .append(
                "inativo",
                lote.getBoolean(
                    "inativo",
                    false
                )
            )
            .append(
                "possuiMovimentacoes",
                possuiMovimentacoes(id)
            );

        responderJSON(
            req,
            200,
            new Document("lote", resposta)
        );
    }

    // =====================================================
    // CONSULTAR MOVIMENTAÇÕES
    // =====================================================

    private boolean possuiMovimentacoes(
        ObjectId loteId
    ) {

        return movimentacoes.countDocuments(
            eq("loteId", loteId)
        ) > 0;
    }

    // =====================================================
    // EDITAR LOTE
    // =====================================================

    private void atualizarLote(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document existente = lotes.find(
            eq("_id", id)
        ).first();

        if (existente == null) {

            responder(
                req,
                404,
                "Lote nao encontrado."
            );

            return;
        }

        Document dados = lerDados(req);

        // ID INDICA QUE ESTAMOS EDITANDO
        Document alteracoes = validarLote(
            dados,
            id
        );

        String codigo =
            alteracoes.getString("codigo");

        // Verifica código duplicado.
        Document duplicado = lotes.find(
            and(
                eq("codigo", codigo),
                ne("_id", id)
            )
        ).first();

        if (duplicado != null) {

            responder(
                req,
                409,
                "Ja existe um lote com este codigo."
            );

            return;
        }

        ObjectId itemAtual =
            existente.getObjectId("itemId");

        ObjectId itemNovo =
            alteracoes.getObjectId("itemId");

        BigDecimal quantidadeAtual =
            obterDecimal(
                existente,
                "quantidadeInicial"
            );

        BigDecimal quantidadeNova =
            obterDecimal(
                alteracoes,
                "quantidadeInicial"
            );

        BigDecimal saldoAtual =
            obterDecimal(
                existente,
                "quantidadeDisponivel"
            );

        boolean temMovimentacoes =
            possuiMovimentacoes(id);

        boolean mudouProduto =
            !itemAtual.equals(itemNovo);

        boolean mudouQuantidade =
            quantidadeAtual.compareTo(
                quantidadeNova
            ) != 0;

        // Impede alteração de produto e quantidade
        // quando o lote possui movimentações.
        if (
            temMovimentacoes &&
            (mudouProduto || mudouQuantidade)
        ) {

            responder(
                req,
                409,
                "Este lote possui movimentacoes. " +
                "Nao e permitido alterar o produto " +
                "ou a quantidade recebida."
            );

            return;
        }

        // Protege a quantidade disponível.
        if (
            mudouQuantidade &&
            saldoAtual.compareTo(
                quantidadeAtual
            ) != 0
        ) {

            responder(
                req,
                409,
                "A quantidade nao pode ser alterada " +
                "porque o saldo disponivel ja foi modificado."
            );

            return;
        }

        // Se não houver movimentações,
        // a quantidade disponível acompanha
        // a nova quantidade recebida.
        if (mudouQuantidade) {

            alteracoes.append(
                "quantidadeDisponivel",
                alteracoes.get("quantidadeInicial")
            );
        }

        alteracoes.append(
            "atualizadoEm",
            new Date()
        );

        // Evita sobrescrever o saldo alterado
        // por outra operação durante a edição.
        UpdateResult resultado = lotes.updateOne(
            and(
                eq("_id", id),
                eq(
                    "quantidadeDisponivel",
                    existente.get("quantidadeDisponivel")
                ),
                eq(
                    "quantidadeInicial",
                    existente.get("quantidadeInicial")
                ),
                eq("itemId", itemAtual)
            ),
            new Document("$set", alteracoes)
        );

        if (resultado.getMatchedCount() == 0) {

            responder(
                req,
                409,
                "Os dados do lote foram modificados. " +
                "Atualize a pagina e tente novamente."
            );

            return;
        }

        responder(
            req,
            200,
            "Lote atualizado com sucesso!"
        );
    }

    // =====================================================
    // VERIFICAR EXCLUSÃO
    // =====================================================

    private void verificarExclusao(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document lote = lotes.find(
            eq("_id", id)
        ).first();

        if (lote == null) {

            responder(
                req,
                404,
                "Lote nao encontrado."
            );

            return;
        }

        boolean temMovimentacoes =
            possuiMovimentacoes(id);

        String mensagem;

        if (temMovimentacoes) {

            mensagem =
                "Este lote nao pode ser excluido, " +
                "pois possui movimentacoes registradas. " +
                "Deseja torna-lo inativo?";

        } else {

            mensagem =
                "Tem certeza de que deseja excluir este lote? " +
                "Esta acao nao podera ser desfeita.";
        }

        responderJSON(
            req,
            200,
            new Document()
                .append(
                    "podeExcluir",
                    !temMovimentacoes
                )
                .append(
                    "possuiMovimentacoes",
                    temMovimentacoes
                )
                .append(
                    "mensagem",
                    mensagem
                )
        );
    }

    // =====================================================
    // EXCLUIR LOTE
    // =====================================================

    private void excluirLote(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document lote = lotes.find(
            eq("_id", id)
        ).first();

        if (lote == null) {

            responder(
                req,
                404,
                "Lote nao encontrado."
            );

            return;
        }

        // Confere movimentações novamente.
        if (possuiMovimentacoes(id)) {

            responderJSON(
                req,
                409,
                new Document()
                    .append(
                        "podeExcluir",
                        false
                    )
                    .append(
                        "mensagem",
                        "Este lote nao pode ser excluido, " +
                        "pois possui movimentacoes registradas. " +
                        "Deseja torna-lo inativo?"
                    )
            );

            return;
        }

        DeleteResult resultado = lotes.deleteOne(
            eq("_id", id)
        );

        if (
            resultado.getDeletedCount() == 0
        ) {

            responder(
                req,
                404,
                "Lote nao encontrado."
            );

            return;
        }

        responder(
            req,
            200,
            "Lote excluido com sucesso!"
        );
    }

    // =====================================================
    // INATIVAR OU REATIVAR LOTE
    // =====================================================

    private void alterarStatus(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document lote = lotes.find(
            eq("_id", id)
        ).first();

        if (lote == null) {

            responder(
                req,
                404,
                "Lote nao encontrado."
            );

            return;
        }

        Document dados = lerDados(req);

        Object valor = dados.get("inativo");

        if (!(valor instanceof Boolean)) {

            responder(
                req,
                400,
                "Status invalido."
            );

            return;
        }

        boolean inativo = (Boolean) valor;

        UpdateResult resultado = lotes.updateOne(
            eq("_id", id),
            new Document(
                "$set",
                new Document()
                    .append(
                        "inativo",
                        inativo
                    )
                    .append(
                        "atualizadoEm",
                        new Date()
                    )
            )
        );

        if (
            resultado.getMatchedCount() == 0
        ) {

            responder(
                req,
                404,
                "Lote nao encontrado."
            );

            return;
        }

        responder(
            req,
            200,
            inativo
                ? "Lote inativado com sucesso!"
                : "Lote reativado com sucesso!"
        );
    }

    // =====================================================
    // LISTAR LOTES
    // =====================================================

    private void listarLotes(HttpExchange req)
        throws IOException {

        List<Document> lista =
            new ArrayList<>();

        for (
            Document lote : lotes.find()
                .sort(
                    new Document("dataEntrada", -1)
                )
        ) {

            ObjectId itemId =
                lote.getObjectId("itemId");

            String nomeProduto =
                "Produto nao encontrado";

            // Busca o nome do produto.
            if (itemId != null) {

                Document produto = itens.find(
                    eq("_id", itemId)
                ).first();

                if (produto != null) {

                    String nome =
                        produto.getString("nome");

                    if (nome != null) {
                        nomeProduto = nome;
                    }
                }
            }

            Document resultado = new Document()
                .append(
                    "id",
                    lote.getObjectId("_id").toHexString()
                )
                .append(
                    "codigo",
                    lote.getString("codigo")
                )
                .append(
                    "produto",
                    nomeProduto
                )
                .append(
                    "itemId",
                    itemId != null
                        ? itemId.toHexString()
                        : null
                )
                .append(
                    "quantidadeInicial",
                    obterDecimal(
                        lote,
                        "quantidadeInicial"
                    ).toPlainString()
                )
                .append(
                    "quantidadeDisponivel",
                    obterDecimal(
                        lote,
                        "quantidadeDisponivel"
                    ).toPlainString()
                )
                .append(
                    "custoTotalAquisicao",
                    obterDecimal(
                        lote,
                        "custoTotalAquisicao"
                    ).toPlainString()
                )
                .append(
                    "dataEntrada",
                    dataTexto(
                        lote.getDate("dataEntrada")
                    )
                )
                .append(
                    "dataValidade",
                    dataTexto(
                        lote.getDate("dataValidade")
                    )
                )
                .append(
                    "observacao",
                    lote.getString("observacao")
                )
                .append(
                    "inativo",
                    lote.getBoolean(
                        "inativo",
                        false
                    )
                );

            lista.add(resultado);
        }

        responderJSON(
            req,
            200,
            new Document(
                "lotes",
                lista
            )
        );
    }

    // =====================================================
    // RESPOSTAS HTTP
    // =====================================================

    private static void responder(
        HttpExchange req,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            req,
            status,
            new Document(
                "mensagem",
                mensagem
            )
        );
    }

    private static void responderJSON(
        HttpExchange req,
        int status,
        Document dados
    ) throws IOException {

        byte[] resposta = dados.toJson()
            .getBytes(StandardCharsets.UTF_8);

        req.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        req.sendResponseHeaders(
            status,
            resposta.length
        );

        try (
            OutputStream saida =
                req.getResponseBody()
        ) {

            saida.write(resposta);
        }
    }
}
