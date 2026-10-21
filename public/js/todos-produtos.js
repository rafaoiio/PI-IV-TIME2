
document.addEventListener("DOMContentLoaded", () => {

    const tabela = document.getElementById("tabelaProdutos");

    const API = "http://localhost:8080/api/produtos";

    // Converte a unidade para um nome amigavel.
    function formatarUnidade(unidade) {

        const unidades = {
            kg: "Quilograma (kg)",
            g: "Grama (g)",
            l: "Litro (L)",
            ml: "Mililitro (ml)",
            un: "Unidade (un)"
        };

        return unidades[unidade] || unidade || "-";
    }

    // Cria uma celula com texto seguro.
    function criarCelula(texto, classes = "") {

        const celula = document.createElement("td");

        celula.className =
            "px-3 py-3 font-semibold " + classes;

        celula.textContent = texto ?? "-";

        return celula;
    }

    // Mostra mensagens na tabela.
    function mostrarMensagem(mensagem) {

        tabela.replaceChildren();

        const linha = document.createElement("tr");
        const celula = document.createElement("td");

        celula.colSpan = 5;
        celula.className =
            "px-3 py-6 text-center text-text-secondary";

        celula.textContent = mensagem;

        linha.appendChild(celula);
        tabela.appendChild(linha);
    }

    // Cria uma linha para cada produto.
    function criarLinhaProduto(produto) {

        const linha = document.createElement("tr");

        linha.className =
            "border-b border-gray-300 bg-[#e9ebee]";

        linha.appendChild(
            criarCelula(produto.nome, "rounded-l-md")
        );

        linha.appendChild(
            criarCelula(produto.categoria)
        );

        linha.appendChild(
            criarCelula(
                formatarUnidade(produto.unidadeMedida)
            )
        );

        const status =
            produto.status === "ativo" ? "Ativo" : "Inativo";

        linha.appendChild(
            criarCelula(status)
        );

        // Celula de acoes.
        const celulaAcoes = document.createElement("td");

        celulaAcoes.className =
            "rounded-r-md px-3 py-3 text-center";

        const grupoBotoes = document.createElement("div");

        grupoBotoes.className =
            "flex items-center justify-center gap-3";

        // Botao de editar.
        const botaoEditar = document.createElement("button");

        botaoEditar.type = "button";
        botaoEditar.title = "Editar produto";
        botaoEditar.setAttribute(
            "aria-label",
            "Editar produto"
        );

        botaoEditar.className =
            "text-primary-dark disabled:opacity-50";

        botaoEditar.innerHTML = `
            <svg xmlns="http://www.w3.org/2000/svg"
                width="20" height="20"
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
                    d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.8 1.8-.06-.06A1.7 1.7 0 0 0 16 18.34a1.7 1.7 0 0 0-1 1.56V20h-3v-.1a1.7 1.7 0 0 0-1-1.56 1.7 1.7 0 0 0-1.94.34l-.06.06-1.8-1.8.06-.06A1.7 1.7 0 0 0 7.6 15a1.7 1.7 0 0 0-1.56-1H6v-3h.04A1.7 1.7 0 0 0 7.6 10a1.7 1.7 0 0 0-.34-1.88L7.2 8.06l1.8-1.8.06.06A1.7 1.7 0 0 0 11 6.66 1.7 1.7 0 0 0 12 5.1V5h3v.1a1.7 1.7 0 0 0 1 1.56 1.7 1.7 0 0 0 1.94-.34l.06-.06 1.8 1.8-.06.06A1.7 1.7 0 0 0 19.4 10a1.7 1.7 0 0 0 1.56 1H21v3h-.04A1.7 1.7 0 0 0 19.4 15Z"/>
            </svg>
        `;

        // Edicao sera integrada na proxima etapa.
        botaoEditar.disabled = true;
        botaoEditar.title =
            "Edicao disponivel na proxima etapa";

        // Botao de excluir.
        const botaoExcluir = document.createElement("button");

        botaoExcluir.type = "button";
        botaoExcluir.title = "Excluir produto";

        botaoExcluir.setAttribute(
            "aria-label",
            "Excluir produto"
        );

        botaoExcluir.className =
            "text-primary-dark disabled:opacity-50";

        botaoExcluir.innerHTML = `
            <svg xmlns="http://www.w3.org/2000/svg"
                width="20" height="20"
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

        // Exclusao so sera liberada apos proteger lotes.
        botaoExcluir.disabled = true;
        botaoExcluir.title =
            "Exclusao disponivel na proxima etapa";

        grupoBotoes.appendChild(botaoEditar);
        grupoBotoes.appendChild(botaoExcluir);

        celulaAcoes.appendChild(grupoBotoes);

        linha.appendChild(celulaAcoes);

        return linha;
    }

    // Busca os produtos no backend.
    async function carregarProdutos() {

        mostrarMensagem("Carregando produtos...");

        try {

            const resposta = await fetch(API);

            if (!resposta.ok) {
                throw new Error(
                    "Nao foi possivel buscar os produtos."
                );
            }

            const dados = await resposta.json();

            tabela.replaceChildren();

            if (!dados.produtos || dados.produtos.length === 0) {

                mostrarMensagem(
                    "Nenhum produto cadastrado."
                );

                return;
            }

            // Preenche a tabela.
            dados.produtos.forEach((produto) => {

                const linha = criarLinhaProduto(produto);

                tabela.appendChild(linha);
            });

        } catch (erro) {

            console.error(erro);

            mostrarMensagem(
                "Erro ao carregar produtos. Verifique o backend."
            );
        }
    }

    // Carrega os produtos quando a pagina abrir.
    carregarProdutos();

});
