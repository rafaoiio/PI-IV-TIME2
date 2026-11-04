document.addEventListener("DOMContentLoaded", () => {

    const API = "http://localhost:8080/api";

    const tabela =
        document.getElementById("tabelaProdutos");

    const mensagemErro =
        document.getElementById("mensagemErro");

    const modal =
        document.getElementById("modalProduto");

    const modalTitulo =
        document.getElementById("modalTitulo");

    const modalTexto =
        document.getElementById("modalTexto");

    const modalConfirmar =
        document.getElementById("modalConfirmar");

    const modalCancelar =
        document.getElementById("modalCancelar");

    let acaoModal = null;

    // ENGRENAGEM
    const iconeEngrenagem = `
        <svg xmlns="http://www.w3.org/2000/svg"
            width="22" height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true">

            <path d="M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z"/>

            <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06-2.84 2.84-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1.06 1.56V21h-4v-.04A1.7 1.7 0 0 0 8.88 19.4 1.7 1.7 0 0 0 7 19.74l-.06.06L4.1 16.96l.06-.06A1.7 1.7 0 0 0 4.5 15a1.7 1.7 0 0 0-1.56-1.06H2.9v-4h.04A1.7 1.7 0 0 0 4.5 8.88 1.7 1.7 0 0 0 4.16 7l-.06-.06L6.94 4.1l.06.06A1.7 1.7 0 0 0 8.88 4.5a1.7 1.7 0 0 0 1.06-1.56V2.9h4v.04A1.7 1.7 0 0 0 15 4.5a1.7 1.7 0 0 0 1.87-.34l.06-.06 2.84 2.84-.06.06A1.7 1.7 0 0 0 19.4 8.88a1.7 1.7 0 0 0 1.56 1.06H21v4h-.04A1.7 1.7 0 0 0 19.4 15Z"/>
        </svg>
    `;

    // LIXEIRA
    const iconeLixeira = `
        <svg xmlns="http://www.w3.org/2000/svg"
            width="22" height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true">

            <path d="M3 6h18M8 6V4h8v2M5 6l1 15h12l1-15M10 10v7M14 10v7"/>
        </svg>
    `;

    function mostrarErro(texto) {

        mensagemErro.textContent = texto;

        mensagemErro.classList.remove("hidden");
    }

    // Lê respostas do backend.
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
                dados.mensagem ||
                dados.erro ||
                `Erro HTTP ${resposta.status}`
            );
        }

        return dados;
    }

    // Identifica o produto pelo ID do MongoDB.
    function obterId(produto) {

        const id =
            produto._id ??
            produto.id ??
            produto.produtoId;

        if (typeof id === "string") {
            return id;
        }

        if (id && typeof id === "object") {
            return id.$oid ?? id.oid ?? null;
        }

        return null;
    }

    function valorTexto(valor, alternativa = "-") {

        if (
            valor === undefined ||
            valor === null ||
            valor === ""
        ) {
            return alternativa;
        }

        if (typeof valor === "object") {
            return valor.nome ||
                valor.$numberDecimal ||
                alternativa;
        }

        return String(valor);
    }

    // Formata validade.
    function formatarData(valor) {

        if (!valor) return "-";

        if (typeof valor === "object") {
            valor = valor.$date || valor.data || null;
        }

        if (!valor) return "-";

        const texto = String(valor);

        if (/^\d{4}-\d{2}-\d{2}/.test(texto)) {

            const [ano, mes, dia] =
                texto.slice(0, 10).split("-");

            return `${dia}/${mes}/${ano}`;
        }

        return texto;
    }

    // Formata valores monetários.
    function formatarPreco(valor) {

        if (
            valor === null ||
            valor === undefined ||
            valor === ""
        ) {
            return "-";
        }

        if (typeof valor === "object") {
            valor = valor.$numberDecimal;
        }

        const numero = Number(valor);

        return Number.isFinite(numero)
            ? numero.toLocaleString("pt-BR", {
                style: "currency",
                currency: "BRL"
            })
            : "-";
    }

    function criarCelula(linha, texto, classeExtra = "") {

        const celula = document.createElement("td");

        celula.className = `px-3 py-3 ${classeExtra}`;

        celula.textContent = texto;

        linha.appendChild(celula);

        return celula;
    }

    // Fecha a janela de confirmação.
    function fecharModal() {

        modal.classList.add("hidden");

        modal.classList.remove("flex");

        acaoModal = null;
    }

    // Abre a janela de confirmação.
    function abrirModal(titulo, texto, botao, acao) {

        modalTitulo.textContent = titulo;

        modalTexto.textContent = texto;

        modalConfirmar.textContent = botao;

        modalConfirmar.disabled = false;

        acaoModal = acao;

        modal.classList.remove("hidden");

        modal.classList.add("flex");

        modalCancelar.focus();
    }

    modalCancelar.addEventListener("click", fecharModal);

    modal.addEventListener("click", (evento) => {

        if (evento.target === modal) {
            fecharModal();
        }
    });

    document.addEventListener("keydown", (evento) => {

        if (
            evento.key === "Escape" &&
            !modal.classList.contains("hidden")
        ) {
            fecharModal();
        }
    });

    // Executa a ação somente após confirmação.
    modalConfirmar.addEventListener("click", async () => {

        if (!acaoModal) return;

        modalConfirmar.disabled = true;

        const acao = acaoModal;

        try {

            await acao();

            fecharModal();

            await carregarProdutos();

        } catch (erro) {

            fecharModal();

            mostrarErro(erro.message);

        } finally {

            modalConfirmar.disabled = false;
        }
    });

    // Verifica no backend se o produto pode ser excluído.
    async function conferirExclusao(id) {

        mensagemErro.classList.add("hidden");

        try {

            const resposta = await fetch(
                `${API}/produtos/${encodeURIComponent(id)}/exclusao`
            );

            const dados = await lerResposta(resposta);

            // Produto sem movimentações ou vínculos.
            if (dados.podeExcluir === true) {

                abrirModal(

                    "Excluir produto",

                    "Tem certeza de que deseja excluir este produto? Esta ação não poderá ser desfeita.",

                    "Excluir produto",

                    async () => {

                        const resposta = await fetch(
                            `${API}/produtos/${encodeURIComponent(id)}`,
                            {
                                method: "DELETE"
                            }
                        );

                        const retorno = await lerResposta(resposta);

                        alert(
                            retorno.mensagem ||
                            "Produto excluído com sucesso!"
                        );
                    }
                );

            } else {

                // Produto possui movimentações ou vínculos.
                abrirModal(

                    "Não é possível excluir o produto",

                    "Este produto não pode ser excluído, pois possui movimentações registradas ou vínculos com lotes. Deseja torná-lo inativo?",

                    "Tornar inativo",

                    async () => {

                        const resposta = await fetch(
                            `${API}/produtos/${encodeURIComponent(id)}/status`,
                            {
                                method: "PATCH",

                                headers: {
                                    "Content-Type": "application/json"
                                },

                                body: JSON.stringify({
                                    status: "inativo"
                                })
                            }
                        );

                        const retorno = await lerResposta(resposta);

                        alert(
                            retorno.mensagem ||
                            "Produto inativado com sucesso!"
                        );
                    }
                );
            }

        } catch (erro) {

            mostrarErro(
                `Não foi possível verificar a exclusão: ${erro.message}`
            );
        }
    }

    // Cria botões com os ícones originais.
    function criarBotao(acao, titulo, icone) {

        const botao = document.createElement("button");

        botao.type = "button";

        botao.title = titulo;

        botao.setAttribute("aria-label", titulo);

        botao.className =
            "flex h-[28px] w-[28px] cursor-pointer items-center justify-center rounded text-primary-dark hover:bg-primary-light";

        botao.innerHTML = icone;

        botao.addEventListener("click", acao);

        return botao;
    }

    // Carrega todos os produtos do MongoDB.
    async function carregarProdutos() {

        mensagemErro.classList.add("hidden");

        tabela.replaceChildren();

        const carregando = document.createElement("tr");

        criarCelula(
            carregando,
            "Carregando produtos..."
        ).colSpan = 8;

        tabela.appendChild(carregando);

        try {

            const resposta = await fetch(`${API}/produtos`);

            const dados = await lerResposta(resposta);

            const produtos = Array.isArray(dados)
                ? dados
                : dados.produtos || dados.itens || [];

            if (!Array.isArray(produtos)) {
                throw new Error("Formato inválido da lista de produtos.");
            }

            tabela.replaceChildren();

            if (produtos.length === 0) {

                const linha = document.createElement("tr");

                const celula = criarCelula(
                    linha,
                    "Nenhum produto cadastrado.",
                    "text-center text-text-secondary"
                );

                celula.colSpan = 8;

                tabela.appendChild(linha);

                return;
            }

            produtos.forEach((produto) => {

                const id = obterId(produto);

                const linha = document.createElement("tr");

                linha.className =
                    "border-b border-gray-300 bg-[#e9ebee]";

                // Nome
                criarCelula(
                    linha,
                    valorTexto(
                        produto.nomeProduto ??
                        produto.nome ??
                        produto.titulo
                    )
                );

                // Categoria
                criarCelula(
                    linha,
                    valorTexto(produto.categoria)
                );

                // Unidade de medida
                const unidade = `
                    ${valorTexto(produto.quantidadeUnidade, "")}
                    ${valorTexto(produto.unidadeMedida, "")}
                `.trim();

                criarCelula(
                    linha,
                    unidade || "-"
                );

                // Quantidade disponível
                criarCelula(
                    linha,
                    `${valorTexto(
                        produto.quantidadeTotal ??
                        produto.quantidadeDisponivel ??
                        0,
                        "0"
                    )} un.`
                );

                // Preço unitário
                criarCelula(
                    linha,
                    formatarPreco(
                        produto.precoUnitario ??
                        produto.valorUnitario
                    )
                );

                // Validade
                criarCelula(
                    linha,
                    formatarData(
                        produto.validade ??
                        produto.dataValidade
                    )
                );

                // Status
                const status = valorTexto(
                    produto.status,
                    "ativo"
                );

                criarCelula(
                    linha,
                    status.charAt(0).toUpperCase() +
                    status.slice(1)
                );

                // AÇÕES
                const acoes = document.createElement("td");

                acoes.className = "px-3 py-3";

                const grupo = document.createElement("div");

                grupo.className =
                    "flex items-center justify-center gap-2";

                // BOTÃO ENGRENAGEM
                const editar = criarBotao(

                    () => {
                        window.location.href =
                            `cadastro-produtos.html?id=${encodeURIComponent(id)}`;
                    },

                    "Editar produto",

                    iconeEngrenagem
                );

                // BOTÃO LIXEIRA
                const excluir = criarBotao(

                    () => conferirExclusao(id),

                    "Excluir produto",

                    iconeLixeira
                );

                if (!id) {

                    editar.disabled = true;

                    excluir.disabled = true;

                    editar.title =
                        "ID do produto não encontrado";

                    excluir.title =
                        "ID do produto não encontrado";
                }

                grupo.append(editar, excluir);

                acoes.appendChild(grupo);

                linha.appendChild(acoes);

                tabela.appendChild(linha);

            });

        } catch (erro) {

            tabela.replaceChildren();

            mostrarErro(
                `Erro ao carregar produtos: ${erro.message}`
            );
        }
    }

    carregarProdutos();

});
