// Dados de exemplo. Depois, estes valores virão do banco de dados.
const produtos = ["Uva", "Pão", "Macarrão", "Tomate"];
const quantidades = [10, 12, 22, 19];

const opcoesPizza = {
  chart: {
    type: "pie",
    height: 280,
    toolbar: { show: false },
  },
  series: quantidades,
  labels: produtos,
  colors: ["#269DD8", "#5145AC", "#E5602D", "#16A34A"],
  legend: { position: "bottom" },
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
  series: [{ name: "Quantidade (kg)", data: quantidades }],
  xaxis: {
    categories: produtos,
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
graficoBarras.render();
