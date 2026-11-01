
document.addEventListener("DOMContentLoaded", () => {

    const API_URL =
        "http://localhost:8080/api/lotes/proximos-vencimento";

    const botoes = document.querySelectorAll("[data-dias]");
    const lista = document.getElementById("lista-vencimentos");
    const mensagem = document.getElementById("mensagem-vencimentos");

    let requisicaoAtual = null;
    let modalAberto = null;

    // Formata uma data ISO para o formato brasileiro
    function formatarData(dataISO) {
        const data = String(dataISO || "").substring(0, 10);

        if (!/^\d{4}-\d{2}-\d{2}$/.test(data)) {
            return "-";
        }

        const [ano, mes, dia] = data.split("-");
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

    // Calcula os dias restantes usando o horário de São Paulo
    function calcularDiasRestantes(dataISO) {
        const data = String(dataISO || "").substring(0, 10);

        if (!/^\d{4}-\d{2}-\d{2}$/.test(data)) {
            return null;
        }

        const [ano, mes, dia] = data.split("-").map(Number);

        const destino = Date.UTC(ano, mes - 1, dia);

        const partes = new Intl.DateTimeFormat("en-US", {
            timeZone: "America/Sao_Paulo",
            year: "numeric",
            month: "2-digit",
            day: "2-digit"
        }).formatToParts(new Date());

        const obter = (tipo) =>
            Number(partes.find((parte) => parte.type === tipo).value);

        const hoje = Date.UTC(
            obter("year"),
            obter("month") - 1,
            obter("day")
        );

        return Math.round(
            (destino - hoje) / (24 * 60 * 60 * 1000)
        );
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

    // Cria elementos de texto para o modal
    function criarTexto(tag, texto, estilos = {}) {
        const elemento = document.createElement(tag);
        elemento.textContent = texto;
        Object.assign(elemento.style, estilos);
        return elemento;
    }

    // Abre o modal de detalhes do lote
    function abrirModalDetalhes(lote, botaoOrigem) {

        // Impede que dois modais fiquem abertos.
        if (modalAberto) {
            modalAberto.fechar();
        }

        const diasRestantes = calcularDiasRestantes(
            lote.dataValidade
        );

        let situacao = "Data não disponível";
        let corSituacao = "#64748b";
        let fundoSituacao = "#f1f5f9";

        if (diasRestantes !== null) {
            if (diasRestantes < 0) {
                situacao =
                    `Vencido há ${Math.abs(diasRestantes)} dia(s)`;
                corSituacao = "#b91c1c";
                fundoSituacao = "#fee2e2";
            } else if (diasRestantes === 0) {
                situacao = "Vence hoje";
                corSituacao = "#b91c1c";
                fundoSituacao = "#fee2e2";
            } else if (diasRestantes <= 3) {
                situacao =
                    `Vence em ${diasRestantes} dia(s)`;
                corSituacao = "#92400e";
                fundoSituacao = "#fef3c7";
            } else {
                situacao =
                    `Vence em ${diasRestantes} dia(s)`;
                corSituacao = "#166534";
                fundoSituacao = "#dcfce7";
            }
        }

        // Fundo escurecido
        const overlay = document.createElement("div");

        Object.assign(overlay.style, {
            position: "fixed",
            inset: "0",
            backgroundColor: "rgba(15, 23, 42, 0.55)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "20px",
            zIndex: "9999"
        });

        // Janela central
        const modal = document.createElement("section");
        modal.setAttribute("role", "dialog");
        modal.setAttribute("aria-modal", "true");

        Object.assign(modal.style, {
            backgroundColor: "#ffffff",
            width: "100%",
            maxWidth: "480px",
            borderRadius: "12px",
            padding: "28px",
            boxShadow: "0 20px 50px rgba(0, 0, 0, 0.2)",
            position: "relative",
            maxHeight: "90vh",
            overflowY: "auto",
            color: "#1f2937"
        });

        // Cabeçalho
        const cabecalho = document.createElement("div");

        Object.assign(cabecalho.style, {
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            gap: "16px",
            marginBottom: "10px"
        });

        const titulo = criarTexto(
            "h2",
            "Detalhes do lote",
            {
                fontSize: "21px",
                fontWeight: "700",
                margin: "0"
            }
        );

        const tituloId = "titulo-modal-vencimento";
        titulo.id = tituloId;
        modal.setAttribute("aria-labelledby", tituloId);

        const botaoX = document.createElement("button");
        botaoX.type = "button";
        botaoX.textContent = "×";
        botaoX.setAttribute("aria-label", "Fechar detalhes");

        Object.assign(botaoX.style, {
            border: "none",
            background: "transparent",
            cursor: "pointer",
            fontSize: "29px",
            lineHeight: "1",
            color: "#64748b"
        });

        cabecalho.append(titulo, botaoX);

        const descricao = criarTexto(
            "p",
            "Informações do produto próximo ao vencimento.",
            {
                fontSize: "13px",
                color: "#64748b",
                marginBottom: "22px"
            }
        );

        // Situação do vencimento
        const selo = criarTexto("span", situacao, {
            display: "inline-block",
            backgroundColor: fundoSituacao,
            color: corSituacao,
            padding: "8px 13px",
            borderRadius: "20px",
            fontSize: "13px",
            fontWeight: "700",
            marginBottom: "22px"
        });

        // Área com as informações
        const informacoes = document.createElement("div");

        Object.assign(informacoes.style, {
            backgroundColor: "#f8fafc",
            border: "1px solid #e2e8f0",
            borderRadius: "8px",
            padding: "8px 18px",
            marginBottom: "24px"
        });

        function adicionarCampo(rotulo, valor) {
            const campo = document.createElement("div");

            Object.assign(campo.style, {
                display: "flex",
                justifyContent: "space-between",
                gap: "16px",
                padding: "13px 0",
                borderBottom: "1px solid #e2e8f0"
            });

            const label = criarTexto("span", rotulo, {
                color: "#64748b",
                fontSize: "14px"
            });

            const conteudo = criarTexto(
                "strong",
                valor ?? "-",
                {
                    fontSize: "14px",
                    textAlign: "right",
                    overflowWrap: "anywhere"
                }
            );

            campo.append(label, conteudo);
            informacoes.appendChild(campo);
        }

        adicionarCampo(
            "Produto",
            lote.produtoNome || "Produto sem nome"
        );

        adicionarCampo(
            "Código do lote",
            lote.codigo || "-"
        );

        adicionarCampo(
            "Quantidade disponível",
            formatarQuantidade(
                lote.quantidadeDisponivel,
                lote.unidadeMedida
            )
        );

        adicionarCampo(
            "Data de validade",
            formatarData(lote.dataValidade)
        );

        adicionarCampo(
            "Situação",
            situacao
        );

        // Botão principal
        const rodape = document.createElement("div");

        Object.assign(rodape.style, {
            display: "flex",
            justifyContent: "flex-end"
        });

        const botaoFechar = document.createElement("button");
        botaoFechar.type = "button";
        botaoFechar.textContent = "Fechar";

        Object.assign(botaoFechar.style, {
            backgroundColor: "#334155",
            color: "#ffffff",
            border: "none",
            borderRadius: "7px",
            padding: "11px 26px",
            fontSize: "14px",
            fontWeight: "600",
            cursor: "pointer"
        });

        rodape.appendChild(botaoFechar);

        modal.append(
            cabecalho,
            descricao,
            selo,
            informacoes,
            rodape
        );

        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        const overflowAnterior = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        function fechar() {
            document.removeEventListener(
                "keydown",
                tratarTeclado
            );

            overlay.remove();
            document.body.style.overflow = overflowAnterior;
            modalAberto = null;

            if (botaoOrigem && botaoOrigem.isConnected) {
                botaoOrigem.focus();
            }
        }

        function tratarTeclado(evento) {
            if (evento.key === "Escape") {
                fechar();
            }

            if (evento.key === "Tab") {
                const controles = [botaoX, botaoFechar];
                const primeiro = controles[0];
                const ultimo = controles[controles.length - 1];

                if (evento.shiftKey &&
                    document.activeElement === primeiro) {
                    evento.preventDefault();
                    ultimo.focus();
                } else if (
                    !evento.shiftKey &&
                    document.activeElement === ultimo
                ) {
                    evento.preventDefault();
                    primeiro.focus();
                }
            }
        }

        botaoX.addEventListener("click", fechar);
        botaoFechar.addEventListener("click", fechar);

        overlay.addEventListener("click", (evento) => {
            if (evento.target === overlay) {
                fechar();
            }
        });

        document.addEventListener("keydown", tratarTeclado);

        modalAberto = { fechar };
        botaoX.focus();
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
            abrirModalDetalhes(lote, detalhes);
        });

        acoes.appendChild(detalhes);
        linha.append(produto, quantidade, validade, acoes);

        return linha;
    }

    // Atualiza a aparência dos botões de filtro
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
