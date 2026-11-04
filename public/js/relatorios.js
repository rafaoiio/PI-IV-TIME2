// Dados de exemplo. Depois, estes valores virão do banco de dados.
const API_URL = "http://localhost:8080";

const opcoesPizza = {
  chart: {
    type: "pie",
    height: 280,
    toolbar: { show: false },
  },
  series: [],
  labels: [],
  colors: ["#269DD8", "#5145AC", "#E5602D", "#16A34A"],
  legend: { position: "bottom" },
  noData: {
    text: "Nenhum desperdício registrado",
  },
  dataLabels: { enabled: false },
  stroke: { width: 0 },
};

const graficoPizza = new ApexCharts(
  document.querySelector("#pizza-chart"),
  opcoesPizza,
);
graficoPizza.render();

const opcoesBarras = {
  chart: {
    type: "bar",
    height: 280,
    toolbar: { show: false },
  },
  series: [{ name: "Quantidade Desperdiçada", data: [] }],
  noData: {
    text: "Nenhum desperdício registrado",
  },
  xaxis: {
    categories: [],
    labels: { style: { fontWeight: 400 } },
  },
  yaxis: {
    min: 0,
    labels: { style: { fontWeight: 400 } },
  },
  colors: ["#16474C"],
  plotOptions: { bar: { columnWidth: "65%" } },
  dataLabels: { enabled: false },
  legend: { show: true, showForSingleSeries: true, position: "bottom" },
};

const graficoBarras = new ApexCharts(
  document.querySelector("#barras-chart"),
  opcoesBarras,
);

async function carregarIndicadoresRelatorios() {
  try {
    const resposta = await fetch(
      "http://localhost:8080/api/relatorios/indicadores",
    );

    if (!resposta.ok) {
      throw new Error("Erro ao consultar indicadores");
    }

    const dados = await resposta.json();

    document.querySelector("#indicador-desperdicados").textContent =
      dados.desperdicados;

    document.querySelector("#indicador-registrados").textContent =
      dados.registrados;

    document.querySelector("#indicador-motivo").textContent =
      dados.motivoMaisFrequente ?? "Nenhum";

    // Converte o Decimal128 recebido do MongoDB
    const valorPerdas =
      dados.perdasFinanceiras?.$numberDecimal ?? dados.perdasFinanceiras;

    // Formata o valor como moeda brasileira
    const perdasFormatadas = new Intl.NumberFormat("pt-BR", {
      style: "currency",
      currency: "BRL",
    }).format(Number(valorPerdas));

    document.querySelector("#indicador-perdas").textContent = perdasFormatadas;
  } catch (erro) {
    console.error("Erro ao carregar indicadores:", erro);
  }
}

async function carregarGraficoMotivos() {
  try {
    const resposta = await fetch(
      `${API_URL}/api/relatorios/desperdicios/por-motivo`,
    );

    if (!resposta.ok) {
      throw new Error("Erro ao consultar desperdícios por motivo");
    }

    const dados = await resposta.json();

    const registro = Array.isArray(dados)
      ? dados
      : Object.values(dados).find(Array.isArray);

    if (!registro) {
      throw new Error("Formato inesperado na resposta");
    }

    const motivos = registro.map((registro) => registro.motivo);

    const ocorrencias = registro.map((registro) =>
      Number(registro.totalOcorrencias),
    );

    graficoPizza.updateOptions({
      labels: motivos,
    });

    graficoPizza.updateSeries(ocorrencias);
  } catch (error) {
    console.log("Erro ao carregar gráfico por motivos: ", error);
  }
}

async function carregarGraficoProdutos() {
  try {
    const resposta = await fetch(
      `${API_URL}/api/relatorios/desperdicios/por-produto`,
    );

    if (!resposta.ok) {
      throw new Error("Erro ao consultar desperdícios por produto");
    }

    const dados = await resposta.json();

    const registros = dados.desperdiciosPorProduto;

    if (!Array.isArray(registros)) {
      throw new Error("Formato inesperado na resposta da API");
    }

    const filtroUnidade = document.querySelector("#filtro-unidade");

    // Obtém as unidades disponíveis sem repetições
    const unidades = [
      ...new Set(registros.map((registro) => registro.unidadeMedida)),
    ].filter(Boolean);

    filtroUnidade.replaceChildren();

    if (unidades.length === 0) {
      filtroUnidade.disabled = true;
      return;
    }

    // Cria as opções do select
    for (const unidade of unidades) {
      const opcao = document.createElement("option");

      opcao.value = unidade;
      opcao.textContent = unidade;

      filtroUnidade.appendChild(opcao);
    }

    // Atualiza o gráfico de acordo com a unidade escolhida
    function atualizarBarras() {
      const unidadeSelecionada = filtroUnidade.value;

      const produtosFiltrados = registros.filter(
        (registro) => registro.unidadeMedida === unidadeSelecionada,
      );

      const nomes = produtosFiltrados.map((registro) => registro.nome);

      const quantidades = produtosFiltrados.map((registro) =>
        Number(
          registro.quantidadeTotal?.$numberDecimal ?? registro.quantidadeTotal,
        ),
      );

      graficoBarras.updateOptions({
        xaxis: {
          categories: nomes,
        },
        series: [
          {
            name: `Quantidade desperdiçada (${unidadeSelecionada})`,
            data: quantidades,
          },
        ],
      });
    }

    // Atualiza quando o usuário troca a unidade
    filtroUnidade.addEventListener("change", atualizarBarras);

    // Exibe os dados assim que carregar
    atualizarBarras();
  } catch (erro) {
    console.error("Erro ao carregar gráfico por produto:", erro);
    graficoBarras.updateOptions({
      noData: { text: "Erro ao carregar os dados" },
    });
  }
}

// Arredonda para centavos sem converter os decimais da API em ponto flutuante.
function formatarMoedaCSV(valor) {
  if (valor == null) return "Não disponível";

  const partes = /^(-?)(\d+)(?:\.(\d+))?$/.exec(String(valor));
  if (!partes) throw new Error("Valor monetário inválido no relatório");

  const [, sinal, inteiro, fracao = ""] = partes;
  let centavos = BigInt(inteiro) * 100n + BigInt((fracao + "00").slice(0, 2));
  if (fracao.length > 2 && fracao[2] >= "5") centavos += 1n;

  const reais = (centavos / 100n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const casas = (centavos % 100n).toString().padStart(2, "0");
  return `R$ ${sinal && centavos !== 0n ? "-" : ""}${reais},${casas}`;
}

function gerarCsvFinanceiro(produtos) {
  const linhas = [[
    "Produto",
    "Qtd. Desperdiçada",
    "Valor unit.",
    "Valor total p/ estoque",
    "Valor perdido",
  ]];

  for (const produto of produtos) {
    const quantidade = String(produto.quantidadeDesperdicada ?? "");
    if (!/^-?\d+(?:\.\d+)?$/.test(quantidade)) {
      throw new Error("Quantidade inválida no relatório");
    }

    linhas.push([
      `${produto.nome ?? ""}${produto.unidadeMedida ? ` (${produto.unidadeMedida})` : ""}`,
      quantidade.replace(".", ","),
      formatarMoedaCSV(produto.valorUnitario),
      formatarMoedaCSV(produto.valorTotalEstoque),
      formatarMoedaCSV(produto.valorPerdido),
    ]);
  }

  return "\uFEFF" + linhas.map((linha) => linha.map((valor) => {
    let texto = String(valor);
    // Também protege fórmulas precedidas por espaços ou caracteres de controle.
    if (/^[\s\u0000-\u001f]*[=+\-@]|^[\t\r\n]/u.test(texto)) {
      texto = "'" + texto;
    }
    return `"${texto.replace(/"/g, '""')}"`;
  }).join(";")).join("\r\n");
}

async function exportarRelatorio() {
  const botao = document.querySelector("#exportar-relatorio");
  if (botao.disabled) return;
  const textoOriginal = botao.textContent;

  botao.disabled = true;
  botao.textContent = "Exportando...";

  try {
    const resposta = await fetch(
      `${API_URL}/api/relatorios/financeiro-por-produto`,
    );

    if (!resposta.ok) {
      throw new Error("Erro ao consultar o relatório financeiro");
    }

    const dados = await resposta.json();
    const produtos = dados.financeiroPorProduto;

    if (!Array.isArray(produtos)) {
      throw new Error("Formato inesperado do relatório financeiro");
    }

    if (produtos.length === 0) {
      alert("Não existem desperdícios para exportar.");
      return;
    }

    // Cria o arquivo
    const arquivo = new Blob([gerarCsvFinanceiro(produtos)], {
      type: "text/csv;charset=utf-8;",
    });

    const url = URL.createObjectURL(arquivo);

    // Inicia o download
    const link = document.createElement("a");
    link.href = url;
    link.download = "relatorio-desperdicios.csv";

    document.body.appendChild(link);
    link.click();
    link.remove();

    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (erro) {
    console.error("Erro ao exportar relatório:", erro);
    alert("Não foi possível exportar o relatório.");
  } finally {
    botao.disabled = false;
    botao.textContent = textoOriginal;
  }
}

document
  .querySelector("#exportar-relatorio")
  .addEventListener("click", exportarRelatorio);

carregarIndicadoresRelatorios();
carregarGraficoMotivos();
graficoBarras.render().then(() => {
  carregarGraficoProdutos();
});
