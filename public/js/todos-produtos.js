
document.addEventListener("DOMContentLoaded", () => {

    const tabela =
        document.getElementById("tabelaProdutos");

    const API = "http://localhost:8080/api/produtos";

    // ---------------------------------
    // FORMATACAO DOS DADOS
    // ---------------------------------

    function formatarNumero(numero) {

        const valor = Number(numero);

        if (!Number.isFinite(valor)) {
            return "-";
        }

        return new Intl.NumberFormat("pt-BR", {
            maximumFractionDigits: 3
        }).format(valor);
    }

    // Unidade cadastrada no produto.
    // Exemplo: 1 L, 500 g, 2 un.
    function formatarUnidade(produto) {

        if (
            produto.quantidadeUnidade === null ||
            produto.quantidadeUnidade === undefined
        ) {
            return "-";
        }

        const unidade = {
            kg: "kg",
            g: "g",
            l: "L",
            ml: "ml",
            un: "un."
        };

        const sigla =
            unidade[produto.unidadeMedida] ||
            produto.unidadeMedida ||
            "";

        return (
            formatarNumero(produto.quantidadeUnidade) +
            " " +
            sigla
        );
    }

    // Quantidade total vinda dos lotes.
    function formatarQuantidade(produto) {

        if (
            produto.quantidadeTotal === null ||
            produto.quantidadeTotal === undefined
        ) {
            return "-";
        }

        return formatarNumero(produto.quantidadeTotal) + " un.";
    }

    // Preco unitario em reais.
    function formatarPreco(preco) {

        if (preco === null || preco === undefined) {
            return "-";
        }

        const numero = Number(preco);

        if (!Number.isFinite(numero)) {
            return "-";
        }

        return new Intl.NumberFormat("pt-BR", {
            style: "currency",
            currency: "BRL"
        }).format(numero);
    }

    // Data no formato brasileiro.
    function formatarData(data) {

        if (!data) {
            return "-";
        }

        // Usa a data ISO para evitar mudanca de dia
        // causada pelo fuso horario do navegador.
        const partes = String(data).slice(0, 10).split("-");

        if (partes.length !== 3) {
            return "-";
        }

        const [ano, mes, dia] = partes;

        return `${dia}/${mes}/${ano}`;
    }

    // ---------------------------------
    // ELEMENTOS DA TABELA
    // ---------------------------------

    function criarCelula(texto, classes = "") {

        const celula = document.createElement("td");

        celula.className =
            "px-3 py-3 font-semibold " + classes;

        celula.textContent = texto ?? "-";

        return celula;
    }

    function mostrarMensagem(mensagem) {

        tabela.replaceChildren();

        const linha = document.createElement("tr");
        const celula = document.createElement("td");

        celula.colSpan = 8;

        celula.className =
            "px-3 py-6 text-center text-text-secondary";

        celula.textContent = mensagem;

        linha.appendChild(celula);
        tabela.appendChild(linha);
    }

    // ---------------------------------
    // LINHA DO PRODUTO
    // ---------------------------------

    function criarLinhaProduto(produto) {

        const linha = document.createElement("tr");

        linha.className =
            "border-b border-gray-300 bg-[#e9ebee]";

        // TITULO
        linha.appendChild(
            criarCelula(
                produto.nome,
                "rounded-l-md"
            )
        );

        // CATEGORIA
        linha.appendChild(
            criarCelula(produto.categoria)
        );

        // UNIDADE DE MEDIDA
        linha.appendChild(
            criarCelula(formatarUnidade(produto))
        );

        // QUANTIDADE TOTAL
        linha.appendChild(
            criarCelula(formatarQuantidade(produto))
        );

        // PRECO UNITARIO
        linha.appendChild(
            criarCelula(
                formatarPreco(produto.precoUnitario)
            )
        );

        // VALIDADE
        linha.appendChild(
            criarCelula(
                formatarData(produto.dataValidade)
            )
        );

        // STATUS DO PRODUTO
        const status =
            produto.status === "ativo"
                ? "Ativo"
                : "Inativo";

        linha.appendChild(
            criarCelula(status)
        );

        // ---------------------------------
        // BOTOES DE ACOES
        // ---------------------------------

        const celulaAcoes = document.createElement("td");

        celulaAcoes.className =
            "rounded-r-md px-3 py-3";

        const grupoBotoes = document.createElement("div");

        grupoBotoes.className =
            "flex items-center justify-center gap-3";

        // BOTAO CONFIGURACOES
        const botaoEditar = document.createElement("button");

        botaoEditar.type = "button";
        botaoEditar.disabled = true;

        botaoEditar.title =
            "Edicao sera integrada posteriormente";

        botaoEditar.setAttribute(
            "aria-label",
            "Configuracoes do produto"
        );

        botaoEditar.className =
            "text-primary-dark disabled:opacity-50";

        botaoEditar.innerHTML = `
            <svg xmlns="http://www.w3.org/2000/svg"
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                aria-hidden="true">

                <path stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z"/>

                <path stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.8 1.8-.06-.06A1.7 1.7 0 0 0 16 18.34a1.7 1.7 0 0 0-1 1.56V20h-3v-.1a1.7 1.7 0 0 0-1-1.56 1.7 1.7 0 0 0-1.94.34l-.06.06-1.8-1.8.06-.06A1.7 1.7 0 0 0 7.6 15a1.7 1.7 0 0 0-1.56-1H6v-3h.04A1.7 1.7 0 0 0 7.6 10a1.7 1.7 0 0 0-.34-1.88L7.2 8.06l1.8-1.8.06.06A1.7 1.7 0 0 0 11 6.66 1.7 1.7 0 0 0 12 5.1V5h3v.1a1.7 1.7 0 0 0 1-1.56V5h3v.1a1.7 1.7 0 0 0 1 1.56 1.7 1.7 0 0 0 1.94-.34l.06-.06 1.8 1.8-.06.06A1.7 1.7 0 0 0 19.4 10a1.7 1.7 0 0 0 1.56 1H21v3h-.04A1.7 1.7 0 0 0 19.4 15Z"/>
            </svg>
        `;

        // BOTAO EXCLUIR
        const botaoExcluir = document.createElement("button");

        botaoExcluir.type = "button";
        botaoExcluir.disabled = true;

        botaoExcluir.title =
            "Exclusao sera integrada posteriormente";

        botaoExcluir.setAttribute(
            "aria-label",
            "Excluir produto"
        );

        botaoExcluir.className =
            "text-primary-dark disabled:opacity-50";

        botaoExcluir.innerHTML = `
            <svg xmlns="http://www.w3.org/2000/svg"
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                aria-hidden="true">

                <path stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14M10 10v7M14 10v7"/>
            </svg>
        `;

        grupoBotoes.appendChild(botaoEditar);
        grupoBotoes.appendChild(botaoExcluir);

        celulaAcoes.appendChild(grupoBotoes);
        linha.appendChild(celulaAcoes);

        return linha;
    }

    // ---------------------------------
    // BUSCAR PRODUTOS DO BACKEND
    // ---------------------------------

    async function carregarProdutos() {

        mostrarMensagem("Carregando produtos...");

        try {

            const resposta = await fetch(API);

            if (!resposta.ok) {
                throw new Error(
                    "Nao foi possivel carregar os produtos."
                );
            }

            const dados = await resposta.json();

            tabela.replaceChildren();

            if (
                !Array.isArray(dados.produtos) ||
                dados.produtos.length === 0
            ) {

                mostrarMensagem(
                    "Nenhum produto cadastrado."
                );

                return;
            }

            dados.produtos.forEach((produto) => {

                const linha = criarLinhaProduto(produto);

                tabela.appendChild(linha);
            });

        } catch (erro) {

            console.error(
                "Erro ao carregar produtos:",
                erro
            );

            mostrarMensagem(
                "Erro ao carregar produtos. Verifique o backend."
            );
        }
    }

    carregarProdutos();

});
