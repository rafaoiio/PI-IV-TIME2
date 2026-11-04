
document.addEventListener("DOMContentLoaded", () => {

    const API = "http://localhost:8080/api";

    const tabela = document.getElementById("tabelaLotes");
    const mensagemErro = document.getElementById("mensagemErro");

    const modal = document.getElementById("modalLote");
    const modalTitulo = document.getElementById("modalTitulo");
    const modalTexto = document.getElementById("modalTexto");
    const modalConfirmar = document.getElementById("modalConfirmar");
    const modalCancelar = document.getElementById("modalCancelar");

    let acaoModal = null;

    // ÍCONE ORIGINAL DA ENGRENAGEM
    const engrenagem = `
        <svg
            xmlns="http://www.w3.org/2000/svg"
            width="22"
            height="22"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            stroke-width="1.8"
            aria-hidden="true"
        >
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.757.426 1.757 2.924 0 3.35a1.724 1.724 0 0 0-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 0 0-2.572 1.065c-.426 1.757-2.924 1.757-3.35 0a1.724 1.724 0 0 0-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 0 0-1.065-2.572c-1.757-.426-1.757-2.924 0-3.35a1.724 1.724 0 0 0 1.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065Z"
            />
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"
            />
        </svg>
    `;

    // ÍCONE ORIGINAL DA LIXEIRA
    const lixeira = `
        <svg xmlns="http://www.w3.org/2000/svg"
            width="22" height="22" fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor" stroke-width="1.8"
            aria-hidden="true">
            <path stroke-linecap="round"
                stroke-linejoin="round"
                d="M3 6h18m-2 0-1 14H6L5 6m3 0V4h8v2M10 10v7m4-7v7"/>
        </svg>
    `;

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

    function mostrarErro(texto) {
        mensagemErro.textContent = texto;
        mensagemErro.classList.remove("hidden");
    }

    function limparErro() {
        mensagemErro.textContent = "";
        mensagemErro.classList.add("hidden");
    }

    function formatarMoeda(valor) {
        const numero = Number(valor);

        return Number.isFinite(numero) && valor != null
            ? numero.toLocaleString("pt-BR", {
                style: "currency",
                currency: "BRL"
            })
            : "-";
    }

    function formatarQuantidade(valor) {
        const numero = Number(valor);

        return Number.isFinite(numero) && valor != null
            ? numero.toLocaleString("pt-BR", {
                maximumFractionDigits: 3
            })
            : "-";
    }

    function formatarData(valor) {
        if (!valor) return "-";

        const texto = String(valor).slice(0, 10);
        const partes = texto.split("-");

        if (partes.length !== 3) return "-";

        return `${partes[2]}/${partes[1]}/${partes[0]}`;
    }

    // PRIORIDADE PARA LOTES INATIVOS
    function calcularStatus(lote) {

        if (lote.inativo === true) {
            return "Inativo";
        }

        const quantidade = Number(lote.quantidadeDisponivel);

        if (quantidade <= 0) {
            return "Esgotado";
        }

        const validade = String(lote.dataValidade || "").slice(0, 10);

        if (!/^\d{4}-\d{2}-\d{2}$/.test(validade)) {
            return "Ativo";
        }

        const hoje = new Date();

        const dataHoje = [
            hoje.getFullYear(),
            String(hoje.getMonth() + 1).padStart(2, "0"),
            String(hoje.getDate()).padStart(2, "0")
        ].join("-");

        if (validade < dataHoje) {
            return "Vencido";
        }

        const dias = Math.round(
            (
                new Date(`${validade}T00:00:00`) -
                new Date(`${dataHoje}T00:00:00`)
            ) / 86400000
        );

        if (dias <= 30) {
            return "Próx. do vencimento";
        }

        return "Ativo";
    }

    function criarCelula(texto, classes = "") {
        const td = document.createElement("td");

        td.className = `px-3 py-3 font-semibold ${classes}`;
        td.textContent = String(texto);

        return td;
    }

    function criarBotao(titulo, icone, acao) {

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

    // MODAL
    function abrirModal(titulo, texto, nomeBotao, acao) {

        modalTitulo.textContent = titulo;
        modalTexto.textContent = texto;
        modalConfirmar.textContent = nomeBotao;
        modalConfirmar.disabled = false;

        acaoModal = acao;

        modal.classList.remove("hidden");
        modal.classList.add("flex");

        modalCancelar.focus();
    }

    function fecharModal() {
        modal.classList.add("hidden");
        modal.classList.remove("flex");
        acaoModal = null;
    }

    modalCancelar.addEventListener("click", fecharModal);

    modal.addEventListener("click", evento => {
        if (evento.target === modal) {
            fecharModal();
        }
    });

    document.addEventListener("keydown", evento => {
        if (evento.key === "Escape" &&
            !modal.classList.contains("hidden") &&
            !modalConfirmar.disabled) {
            fecharModal();
        }
    });

    modalConfirmar.addEventListener("click", async () => {

        if (!acaoModal || modalConfirmar.disabled) return;

        const acao = acaoModal;
        modalConfirmar.disabled = true;
        modalCancelar.disabled = true;

        try {
            const mensagem = await acao();

            fecharModal();

            await carregarLotes();

            alert(mensagem);

        } catch (erro) {
            mostrarErro(erro.message);

        } finally {
            modalConfirmar.disabled = false;
            modalCancelar.disabled = false;
        }
    });

    // VERIFICA MOVIMENTAÇÕES ANTES DE EXCLUIR
    async function verificarExclusao(id) {

        limparErro();

        try {
            const resposta = await fetch(
                `${API}/lotes/${encodeURIComponent(id)}/exclusao`
            );

            const dados = await lerResposta(resposta);

            if (dados.podeExcluir === true) {

                abrirModal(
                    "Excluir lote",
                    "Tem certeza de que deseja excluir este lote? Esta ação não poderá ser desfeita.",
                    "Excluir lote",
                    async () => {

                        const resposta = await fetch(
                            `${API}/lotes/${encodeURIComponent(id)}`,
                            { method: "DELETE" }
                        );

                        const resultado = await lerResposta(resposta);

                        return resultado.mensagem ||
                            "Lote excluído com sucesso!";
                    }
                );

            } else {

                abrirModal(
                    "Não é possível excluir o lote",
                    dados.mensagem ||
                    "Este lote não pode ser excluído, pois possui movimentações registradas. Deseja torná-lo inativo?",
                    "Tornar inativo",
                    async () => {

                        const resposta = await fetch(
                            `${API}/lotes/${encodeURIComponent(id)}/status`,
                            {
                                method: "PATCH",
                                headers: {
                                    "Content-Type": "application/json"
                                },
                                body: JSON.stringify({
                                    inativo: true
                                })
                            }
                        );

                        const resultado = await lerResposta(resposta);

                        return resultado.mensagem ||
                            "Lote inativado com sucesso!";
                    }
                );
            }

        } catch (erro) {
            mostrarErro(
                `Não foi possível verificar o lote: ${erro.message}`
            );
        }
    }

    function criarLinha(lote) {

        const id = lote.id;

        const linha = document.createElement("tr");

        linha.className =
            "border-b border-gray-300 bg-[#e9ebee]";

        linha.appendChild(
            criarCelula(lote.codigo || "-", "rounded-l-md")
        );

        linha.appendChild(
            criarCelula(lote.produto || "Produto não encontrado")
        );

        linha.appendChild(
            criarCelula(formatarQuantidade(lote.quantidadeInicial))
        );

        linha.appendChild(
            criarCelula(formatarQuantidade(lote.quantidadeDisponivel))
        );

        linha.appendChild(
            criarCelula(formatarMoeda(lote.custoTotalAquisicao))
        );

        linha.appendChild(
            criarCelula(formatarData(lote.dataEntrada))
        );

        linha.appendChild(
            criarCelula(formatarData(lote.dataValidade))
        );

        linha.appendChild(
            criarCelula(calcularStatus(lote), "text-center")
        );

        // AÇÕES
        const celula = document.createElement("td");
        celula.className = "rounded-r-md px-3 py-3";

        const grupo = document.createElement("div");
        grupo.className = "flex items-center justify-center gap-3";

        const editar = criarBotao(
            "Editar lote",
            engrenagem,
            () => {
                window.location.href =
                    `cadastro-lotes.html?id=${encodeURIComponent(id)}`;
            }
        );

        const excluir = criarBotao(
            "Excluir lote",
            lixeira,
            () => verificarExclusao(id)
        );

        if (!id) {
            editar.disabled = true;
            excluir.disabled = true;
            editar.title = "ID do lote não encontrado";
            excluir.title = "ID do lote não encontrado";
        }

        grupo.append(editar, excluir);
        celula.appendChild(grupo);
        linha.appendChild(celula);

        return linha;
    }

    function mostrarMensagem(texto) {
        tabela.replaceChildren();

        const linha = document.createElement("tr");
        const celula = document.createElement("td");

        celula.colSpan = 9;
        celula.className =
            "px-3 py-6 text-center text-text-secondary";

        celula.textContent = texto;
        linha.appendChild(celula);
        tabela.appendChild(linha);
    }

    async function carregarLotes() {

        limparErro();
        mostrarMensagem("Carregando lotes...");

        try {
            const resposta = await fetch(`${API}/lotes`);

            const dados = await lerResposta(resposta);

            if (!Array.isArray(dados.lotes)) {
                throw new Error("Resposta inválida do servidor.");
            }

            tabela.replaceChildren();

            if (dados.lotes.length === 0) {
                mostrarMensagem("Nenhum lote cadastrado.");
                return;
            }

            dados.lotes.forEach(lote => {
                tabela.appendChild(criarLinha(lote));
            });

        } catch (erro) {
            console.error(erro);
            mostrarMensagem("Não foi possível carregar os lotes.");
            mostrarErro(erro.message);
        }
    }

    carregarLotes();

});
