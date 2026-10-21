
const API_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", () => {
    const tabela = document.getElementById("historico-desperdicio");
    const token = sessionStorage.getItem("token");

    if (!token) {
        window.location.href = "login-usuario.html";
        return;
    }

    function formatarData(data) {
        if (!data) return "-";

        const valor = String(data).substring(0, 10);
        const partes = valor.split("-");

        if (partes.length !== 3) return valor;

        return `${partes[2]}/${partes[1]}/${partes[0]}`;
    }

    function formatarQuantidade(quantidade, unidade) {
        const numero = Number(quantidade);

        if (!Number.isFinite(numero)) return "-";

        return `${numero.toLocaleString("pt-BR")} ${unidade || ""}`;
    }

    function criarCelula(linha, texto, centralizar = false) {
        const celula = document.createElement("td");

        celula.textContent = texto ?? "-";

        celula.className = centralizar
            ? "px-6 py-2 text-center"
            : "px-6 py-2";

        linha.appendChild(celula);
    }

    function mostrarMensagem(texto) {
        tabela.replaceChildren();

        const linha = document.createElement("tr");
        const celula = document.createElement("td");

        celula.colSpan = 5;
        celula.textContent = texto;
        celula.className = "px-6 py-6 text-center text-text-secondary";

        linha.appendChild(celula);
        tabela.appendChild(linha);
    }

    function preencherTabela(registros) {
        tabela.replaceChildren();

        if (!registros.length) {
            mostrarMensagem("Nenhuma saída encontrada.");
            return;
        }

        registros.forEach((registro, indice) => {
            const linha = document.createElement("tr");

            linha.className = `
                text-[14px]
                font-semibold
                text-text
                ${indice % 2 === 0 ? "bg-[#E4E5E5]" : "bg-[#F1F1F1]"}
            `;

            const responsavel = registro.origem === "AUTOMATICA"
                ? "Sistema"
                : registro.responsavelNome || "Não informado";

            criarCelula(
                linha,
                registro.produtoNome || "Produto não identificado"
            );

            criarCelula(
                linha,
                formatarQuantidade(
                    registro.quantidade,
                    registro.unidade
                ),
                true
            );

            criarCelula(
                linha,
                registro.motivo || "-",
                true
            );

            criarCelula(
                linha,
                formatarData(registro.dataOcorrencia),
                true
            );

            criarCelula(
                linha,
                responsavel,
                true
            );

            tabela.appendChild(linha);
        });
    }

    async function carregarHistorico() {
        mostrarMensagem("Carregando histórico...");

        try {
            let registros = [];
            let pagina = 1;
            let total = 0;

            do {
                const parametros = new URLSearchParams({
                    pagina: pagina,
                    limite: 100
                });

                const resposta = await fetch(
                    `${API_URL}/api/saidas?${parametros.toString()}`,
                    {
                        method: "GET",
                        headers: {
                            "Authorization": `Bearer ${token}`
                        }
                    }
                );

                if (resposta.status === 401) {
                    sessionStorage.removeItem("token");
                    sessionStorage.removeItem("usuario");
                    window.location.href = "login-usuario.html";
                    return;
                }

                if (!resposta.ok) {
                    throw new Error("Erro ao consultar o histórico.");
                }

                const dados = await resposta.json();

                registros.push(...(dados.itens || []));
                total = dados.total || 0;
                pagina++;

            } while (registros.length < total && pagina <= 100);

            preencherTabela(registros);

        } catch (erro) {
            console.error("Erro ao carregar histórico:", erro);

            mostrarMensagem(
                "Não foi possível carregar o histórico."
            );
        }
    }

    carregarHistorico();
});
