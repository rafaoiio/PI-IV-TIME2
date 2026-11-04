
document.addEventListener("DOMContentLoaded", () => {

    const API = "http://localhost:8080/api";

    const formulario = document.getElementById("formCadastroLote");

    const campoProduto = document.getElementById("produto");
    const campoCodigo = document.getElementById("codigoLote");
    const campoQuantidade = document.getElementById("quantidadeRecebida");
    const campoEntrada = document.getElementById("dataEntrada");
    const campoValidade = document.getElementById("dataValidade");
    const campoValorUnitario = document.getElementById("valorUnitario");
    const campoCustoTotal = document.getElementById("custoTotalAquisicao");
    const campoObservacao = document.getElementById("observacao");

    const botaoSalvar = document.getElementById("botaoSalvar");
    const botaoCancelar = document.getElementById("botaoCancelar");
    const mensagem = document.getElementById("mensagemFormulario");

    const parametros = new URLSearchParams(window.location.search);
    const loteId = parametros.get("id");

    const editando = Boolean(loteId);

    let carregado = !editando;

    function mostrarErro(texto) {
        mensagem.textContent = texto;
        mensagem.classList.remove("hidden");
    }

    function limparErro() {
        mensagem.textContent = "";
        mensagem.classList.add("hidden");
    }

    async function lerResposta(resposta) {

        const texto = await resposta.text();
        let dados = {};

        if (texto) {
            try {
                dados = JSON.parse(texto);
            } catch {
                dados = { mensagem: texto };
            }
        }

        if (!resposta.ok) {
            throw new Error(
                dados.mensagem || `Erro HTTP ${resposta.status}`
            );
        }

        return dados;
    }

    // CARREGAR PRODUTOS
    async function carregarProdutos() {

        campoProduto.disabled = true;
        campoProduto.replaceChildren();

        const carregando = document.createElement("option");
        carregando.value = "";
        carregando.textContent = "Carregando produtos...";
        campoProduto.appendChild(carregando);

        const resposta = await fetch(`${API}/produtos`);
        const dados = await lerResposta(resposta);

        if (!Array.isArray(dados.produtos)) {
            throw new Error("Resposta de produtos inválida.");
        }

        campoProduto.replaceChildren();

        const inicial = document.createElement("option");
        inicial.value = "";
        inicial.textContent = "Selecione um produto cadastrado";
        campoProduto.appendChild(inicial);

        dados.produtos.forEach(produto => {

            if (!produto.id || !produto.nome) return;

            // Produtos inativos não podem receber lotes novos.
            if (!editando && produto.status === "inativo") return;

            const opcao = document.createElement("option");
            opcao.value = produto.id;
            opcao.textContent = produto.nome;

            campoProduto.appendChild(opcao);
        });

        campoProduto.disabled = false;
    }

    function calcularCustoTotal() {

        const quantidade = Number(campoQuantidade.value);
        const valor = Number(campoValorUnitario.value);

        if (
            !campoQuantidade.value ||
            !campoValorUnitario.value ||
            !Number.isFinite(quantidade) ||
            !Number.isFinite(valor) ||
            quantidade <= 0 ||
            valor <= 0
        ) {
            campoCustoTotal.value = "";
            return;
        }

        campoCustoTotal.value =
            (quantidade * valor).toFixed(2);
    }

    campoQuantidade.addEventListener("input", calcularCustoTotal);
    campoValorUnitario.addEventListener("input", calcularCustoTotal);

    formulario.addEventListener("reset", () => {
        campoCustoTotal.value = "";
    });

    // ADAPTA O FORMULÁRIO
    if (editando) {

        document.title =
            "Editar Lote | Sistema de Gestão e Redução de Desperdício";

        document.getElementById("caminhoPagina").textContent =
            "/ Editar Lote";

        document.getElementById("tituloFormulario").textContent =
            "Editar lote";

        botaoSalvar.textContent = "Salvar alterações";
        botaoSalvar.disabled = true;
    }

    // CARREGAR O LOTE PARA EDIÇÃO
    async function carregarLote() {

        const resposta = await fetch(
            `${API}/lotes/${encodeURIComponent(loteId)}`
        );

        const dados = await lerResposta(resposta);
        const lote = dados.lote || dados;

        campoProduto.value = lote.itemId || "";

        if (!campoProduto.value) {
            throw new Error("Produto do lote não encontrado na lista.");
        }

        campoCodigo.value = lote.codigo || "";

        campoQuantidade.value =
            lote.quantidadeInicial ?? "";

        campoEntrada.value =
            String(lote.dataEntrada || "").slice(0, 10);

        campoValidade.value =
            String(lote.dataValidade || "").slice(0, 10);

        campoObservacao.value = lote.observacao || "";

        // Recupera valor unitário usando o custo salvo.
        const quantidade = Number(lote.quantidadeInicial);
        const custo = Number(lote.custoTotalAquisicao);

        if (quantidade > 0 && Number.isFinite(custo)) {
            campoValorUnitario.value =
                (custo / quantidade).toFixed(2);
        }

        calcularCustoTotal();

        if (lote.possuiMovimentacoes === true) {
            campoProduto.disabled = true;
            campoQuantidade.readOnly = true;

            campoProduto.title =
                "Produto bloqueado: o lote possui movimentações.";

            campoQuantidade.title =
                "Quantidade bloqueada: o lote possui movimentações.";
        }

        carregado = true;
        botaoSalvar.disabled = false;
    }

    botaoCancelar.addEventListener("click", () => {
        window.location.href = "todos-lotes.html";
    });

    // ENVIA CADASTRO OU EDIÇÃO
    formulario.addEventListener("submit", async evento => {

        evento.preventDefault();
        limparErro();

        if (!carregado) {
            mostrarErro("O lote ainda não foi carregado.");
            return;
        }

        if (!formulario.reportValidity()) return;

        const lote = {
            itemId: campoProduto.value,
            codigo: campoCodigo.value.trim(),
            quantidadeRecebida: campoQuantidade.value,
            dataEntrada: campoEntrada.value,
            dataValidade: campoValidade.value,
            valorUnitario: campoValorUnitario.value,
            observacao: campoObservacao.value.trim()
        };

        if (!lote.itemId) {
            mostrarErro("Selecione um produto.");
            return;
        }

        if (lote.dataValidade < lote.dataEntrada) {
            mostrarErro(
                "A validade não pode ser anterior à data de entrada."
            );
            return;
        }

        botaoSalvar.disabled = true;
        botaoSalvar.textContent =
            editando ? "Salvando..." : "Cadastrando...";

        try {

            const url = editando
                ? `${API}/lotes/${encodeURIComponent(loteId)}`
                : `${API}/lotes`;

            const resposta = await fetch(url, {
                method: editando ? "PUT" : "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify(lote)
            });

            const resultado = await lerResposta(resposta);

            alert(
                resultado.mensagem ||
                (editando
                    ? "Lote atualizado com sucesso!"
                    : "Lote cadastrado com sucesso!")
            );

            if (editando) {
                window.location.href = "todos-lotes.html";
            } else {
                formulario.reset();
                campoCustoTotal.value = "";
            }

        } catch (erro) {
            console.error(erro);
            mostrarErro(erro.message);

        } finally {
            botaoSalvar.disabled = false;
            botaoSalvar.textContent = editando
                ? "Salvar alterações"
                : "Cadastrar novo lote";
        }
    });

    // INICIALIZAÇÃO
    async function iniciar() {
        try {
            await carregarProdutos();

            if (editando) {
                await carregarLote();
            }

        } catch (erro) {
            console.error(erro);
            mostrarErro(erro.message);
            botaoSalvar.disabled = true;
        }
    }

    iniciar();

});
