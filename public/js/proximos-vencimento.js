
document.addEventListener("DOMContentLoaded", () => {

    const API_URL =
        "http://localhost:8080/api/lotes/proximos-vencimento";

    const botoes = document.querySelectorAll("[data-dias]");
    const lista = document.getElementById("lista-vencimentos");
    const mensagem = document.getElementById("mensagem-vencimentos");

    let requisicaoAtual = null;

    // Formata uma data ISO para o formato brasileiro
    function formatarData(dataISO) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(dataISO || "")) {
            return "-";
        }

        const [ano, mes, dia] = dataISO.split("-");
        return `${dia}/${mes}/${ano}`;
    }

    // Formata a quantidade com a unidade do produto
    function formatarQuantidade(quantidade, unidade) {
        const numero = Number(quantidade);

        if (!Number.isFinite(numero)) {
            return "-";
        }

        return new Intl.NumberFormat("pt-BR", {
            maximumFractionDigits: 6
        }).format(numero) + " " + (unidade || "");
    }

    // Exibe mensagens de carregamento e erro
    function mostrarMensagem(texto) {
        mensagem.textContent = texto;
        mensagem.classList.remove("hidden");
        mensagem.hidden = false;
    }

    function esconderMensagem() {
        mensagem.classList.add("hidden");
        mensagem.hidden = true;
    }

    // Cria uma linha da tabela usando os dados da API
    function criarLinha(lote) {
        const linha = document.createElement("div");

        linha.className =
            "grid min-h-[48px] " +
            "grid-cols-[1fr_1fr_1.2fr_180px] " +
            "items-center border-b border-gray-300 " +
            "text-center text-text";

        const produto = document.createElement("div");
        produto.className = "flex flex-col px-2 py-2";

        const nome = document.createElement("span");
        nome.className = "text-sm font-bold";
        nome.textContent = lote.produtoNome || "Produto sem nome";

        const codigo = document.createElement("span");
        codigo.className = "text-xs text-text-secondary";
        codigo.textContent = "Lote: " + (lote.codigo || "-");

        produto.append(nome, codigo);

        const quantidade = document.createElement("span");
        quantidade.className = "px-2 text-sm font-semibold";
        quantidade.textContent = formatarQuantidade(
            lote.quantidadeDisponivel,
            lote.unidadeMedida
        );

        const validade = document.createElement("span");
        validade.className = "px-2 text-sm font-bold";
        validade.textContent = formatarData(lote.dataValidade);

        const acoes = document.createElement("div");
        acoes.className = "flex justify-center";

        const detalhes = document.createElement("button");
        detalhes.type = "button";
        detalhes.textContent = "Detalhes";
        detalhes.className =
            "cursor-pointer px-2 py-1 text-xs " +
            "font-semibold text-primary hover:underline";

        detalhes.addEventListener("click", () => {
            alert(
                "Produto: " + lote.produtoNome + "\n" +
                "Lote: " + lote.codigo + "\n" +
                "Quantidade: " + formatarQuantidade(
                    lote.quantidadeDisponivel,
                    lote.unidadeMedida
                ) + "\n" +
                "Validade: " + formatarData(lote.dataValidade)
            );
        });

        acoes.appendChild(detalhes);
        linha.append(produto, quantidade, validade, acoes);

        return linha;
    }

    // Atualiza a aparência dos botões
    function selecionarFiltro(botaoAtivo) {
        botoes.forEach((botao) => {
            botao.classList.remove(
                "bg-primary-dark",
                "text-white"
            );

            botao.classList.add(
                "bg-gray-200",
                "text-text"
            );

            botao.setAttribute("aria-pressed", "false");
        });

        botaoAtivo.classList.remove(
            "bg-gray-200",
            "text-text"
        );

        botaoAtivo.classList.add(
            "bg-primary-dark",
            "text-white"
        );

        botaoAtivo.setAttribute("aria-pressed", "true");
    }

    // Consulta a API Java
    async function carregarLotes(dias) {

        if (requisicaoAtual) {
            requisicaoAtual.abort();
        }

        const controlador = new AbortController();
        requisicaoAtual = controlador;

        lista.replaceChildren();
        mostrarMensagem("Carregando lotes...");

        try {
            const resposta = await fetch(
                `${API_URL}?dias=${dias}`,
                {
                    method: "GET",
                    signal: controlador.signal,
                    cache: "no-store"
                }
            );

            if (!resposta.ok) {
                throw new Error(
                    "Erro HTTP " + resposta.status
                );
            }

            const dados = await resposta.json();

            if (!Array.isArray(dados.lotes)) {
                throw new Error("Resposta inválida da API");
            }

            if (dados.lotes.length === 0) {
                mostrarMensagem(
                    "Nenhum lote encontrado para este período."
                );
                return;
            }

            const fragmento = document.createDocumentFragment();

            dados.lotes.forEach((lote) => {
                fragmento.appendChild(criarLinha(lote));
            });

            lista.appendChild(fragmento);
            esconderMensagem();

        } catch (erro) {

            if (erro.name === "AbortError") {
                return;
            }

            console.error("Erro ao carregar lotes:", erro);

            mostrarMensagem(
                "Não foi possível carregar os lotes. " +
                "Verifique se o backend está em execução."
            );

        } finally {
            if (requisicaoAtual === controlador) {
                requisicaoAtual = null;
            }
        }
    }

    // Eventos dos filtros
    botoes.forEach((botao) => {
        botao.addEventListener("click", () => {
            const dias = Number(botao.dataset.dias);

            selecionarFiltro(botao);
            carregarLotes(dias);
        });
    });

    // Carrega inicialmente os próximos 7 dias
    const filtroInicial =
        document.querySelector('[data-dias="7"]');

    if (filtroInicial) {
        selecionarFiltro(filtroInicial);
        carregarLotes(7);
    }
});
