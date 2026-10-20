
document.addEventListener("DOMContentLoaded", function () {

  const formCadastro =
    document.getElementById("formCadastro");

  const etapaDados =
    document.getElementById("etapaDados");

  const etapaSenha =
    document.getElementById("etapaSenha");


  // Campos

  const nomeInput =
    document.getElementById("nome");

  const emailInput =
    document.getElementById("email");

  const celularInput =
    document.getElementById("celular");

  const senhaInput =
    document.getElementById("senha");

  const confirmarSenhaInput =
    document.getElementById("confirmarSenha");


  // Mensagens de erro

  const erroNome =
    document.getElementById("erroNome");

  const erroEmail =
    document.getElementById("erroEmail");

  const erroCelular =
    document.getElementById("erroCelular");

  const erroSenha =
    document.getElementById("erroSenha");

  const erroConfirmarSenha =
    document.getElementById("erroConfirmarSenha");


  // Botões

  const btnProximo =
    document.getElementById("btnProximo");

  const btnVoltar =
    document.getElementById("btnVoltar");

  const btnMostrarSenha =
    document.getElementById("btnMostrarSenha");

  const btnMostrarConfirmacao =
    document.getElementById("btnMostrarConfirmacao");


  let etapaAtual = 1;


  function mostrarErro(
    input,
    elementoErro,
    mensagem
  ) {

    elementoErro.textContent = mensagem;

    elementoErro.classList.remove("hidden");

    input.setAttribute(
      "aria-invalid",
      "true"
    );

  }


  function removerErro(
    input,
    elementoErro
  ) {

    elementoErro.textContent = "";

    elementoErro.classList.add("hidden");

    input.removeAttribute("aria-invalid");

  }


  function validarNome() {

    const nome =
      nomeInput.value.trim();

    const partesNome =
      nome
        .split(/\s+/)
        .filter(parte => parte.length > 0);


    if (nome === "") {

      mostrarErro(
        nomeInput,
        erroNome,
        "Informe seu nome completo."
      );

      return false;

    }


    if (nome.length < nomeInput.minLength) {

      mostrarErro(
        nomeInput,
        erroNome,
        "Digite um nome válido."
      );

      return false;

    }


    if (partesNome.length < 2) {

      mostrarErro(
        nomeInput,
        erroNome,
        "Digite seu nome e sobrenome."
      );

      return false;

    }


    removerErro(
      nomeInput,
      erroNome
    );

    return true;

  }


  function validarEmail() {

    const email =
      emailInput.value.trim();


    if (email === "") {

      mostrarErro(
        emailInput,
        erroEmail,
        "Informe seu e-mail."
      );

      return false;

    }


    if (emailInput.validity.typeMismatch) {

      mostrarErro(
        emailInput,
        erroEmail,
        "Digite um e-mail válido."
      );

      return false;

    }


    removerErro(
      emailInput,
      erroEmail
    );

    return true;

  }


  function obterNumerosCelular(valor) {

    const texto =
      String(valor).trim();

    let numeros =
      texto.replace(/\D/g, "");


    // Remove o código do Brasil (+55)
    // quando ele já estiver presente.

    if (
      texto.startsWith("+55") ||
      (
        numeros.length > 11 &&
        numeros.startsWith("55")
      )
    ) {

      numeros =
        numeros.substring(2);

    }


    return numeros.substring(0, 11);

  }


  function aplicarMascaraCelular(valor) {

    const numeros =
      obterNumerosCelular(valor);


    if (numeros.length === 0) {

      return "";

    }


    let resultado =
      "+55 (" + numeros.substring(0, 2);


    if (numeros.length >= 2) {

      resultado += ")";

    }


    if (numeros.length > 2) {

      resultado +=
        " " + numeros.substring(2, 7);

    }


    if (numeros.length > 7) {

      resultado +=
        "-" + numeros.substring(7, 11);

    }


    return resultado;

  }


  function validarCelular() {

    const numeros =
      obterNumerosCelular(
        celularInput.value
      );


    if (numeros.length === 0) {

      mostrarErro(
        celularInput,
        erroCelular,
        "Informe seu celular."
      );

      return false;

    }


    if (numeros.length !== 11) {

      mostrarErro(
        celularInput,
        erroCelular,
        "Digite um celular válido com DDD."
      );

      return false;

    }


    removerErro(
      celularInput,
      erroCelular
    );

    return true;

  }


  function validarSenha() {

    const senha =
      senhaInput.value;


    if (senha === "") {

      mostrarErro(
        senhaInput,
        erroSenha,
        "Informe sua senha."
      );

      return false;

    }


    if (senha.length < senhaInput.minLength) {

      mostrarErro(
        senhaInput,
        erroSenha,
        "A senha deve ter pelo menos 8 caracteres."
      );

      return false;

    }


    removerErro(
      senhaInput,
      erroSenha
    );

    return true;

  }


  function validarConfirmacaoSenha() {

    const confirmacao =
      confirmarSenhaInput.value;


    if (confirmacao === "") {

      mostrarErro(
        confirmarSenhaInput,
        erroConfirmarSenha,
        "Confirme sua senha."
      );

      return false;

    }


    if (confirmacao !== senhaInput.value) {

      mostrarErro(
        confirmarSenhaInput,
        erroConfirmarSenha,
        "As senhas não coincidem."
      );

      return false;

    }


    removerErro(
      confirmarSenhaInput,
      erroConfirmarSenha
    );

    return true;

  }


  function irParaEtapaSenha() {

    // Valida todos os campos da etapa 1.

    const nomeValido =
      validarNome();

    const emailValido =
      validarEmail();

    const celularValido =
      validarCelular();


    // Se algum campo for inválido,
    // permanece na primeira etapa.

    if (
      !nomeValido ||
      !emailValido ||
      !celularValido
    ) {

      if (!nomeValido) {

        nomeInput.focus();

      } else if (!emailValido) {

        emailInput.focus();

      } else {

        celularInput.focus();

      }

      return;

    }


    // Esconde a primeira etapa.

    etapaDados.classList.add("hidden");


    // Mostra a etapa da senha.

    etapaSenha.classList.remove("hidden");


    etapaAtual = 2;


    senhaInput.focus();

  }


  function voltarParaEtapaDados() {

    etapaSenha.classList.add("hidden");

    etapaDados.classList.remove("hidden");

    etapaAtual = 1;

    nomeInput.focus();

  }


  function alternarVisibilidadeSenha(
    input,
    botao
  ) {

    const estaOculta =
      input.type === "password";


    if (estaOculta) {

      input.type = "text";

      botao.setAttribute(
        "aria-label",
        "Ocultar senha"
      );

    } else {

      input.type = "password";

      botao.setAttribute(
        "aria-label",
        "Mostrar senha"
      );

    }


    botao.setAttribute(
      "aria-pressed",
      String(estaOculta)
    );

  }


  // Valida quando o usuário sai do campo.

  nomeInput.addEventListener(
    "blur",
    validarNome
  );


  emailInput.addEventListener(
    "blur",
    validarEmail
  );


  celularInput.addEventListener(
    "blur",
    validarCelular
  );


  senhaInput.addEventListener(
    "blur",
    validarSenha
  );


  confirmarSenhaInput.addEventListener(
    "blur",
    validarConfirmacaoSenha
  );


  // Validação durante a digitação

  // Nome

  nomeInput.addEventListener(
    "input",
    function () {

      if (
        !erroNome.classList.contains("hidden")
      ) {

        validarNome();

      }

    }
  );


  // E-mail

  emailInput.addEventListener(
    "input",
    function () {

      if (
        !erroEmail.classList.contains("hidden")
      ) {

        validarEmail();

      }

    }
  );


  // Celular

  celularInput.addEventListener(
    "input",
    function () {

      celularInput.value =
        aplicarMascaraCelular(
          celularInput.value
        );


      if (
        !erroCelular.classList.contains("hidden")
      ) {

        validarCelular();

      }

    }
  );


  // Senha

  senhaInput.addEventListener(
    "input",
    function () {

      if (
        !erroSenha.classList.contains("hidden")
      ) {

        validarSenha();

      }


      // Atualiza a confirmação se
      // a senha principal for alterada.

      if (
        !erroConfirmarSenha.classList.contains(
          "hidden"
        )
      ) {

        validarConfirmacaoSenha();

      }

    }
  );


  // Confirmar senha

  confirmarSenhaInput.addEventListener(
    "input",
    function () {

      if (
        !erroConfirmarSenha.classList.contains(
          "hidden"
        )
      ) {

        validarConfirmacaoSenha();

      }

    }
  );


  // Botão Próximo

  btnProximo.addEventListener(
    "click",
    irParaEtapaSenha
  );


  // Botão Voltar

  btnVoltar.addEventListener(
    "click",
    voltarParaEtapaDados
  );


  // Mostrar / esconder senha

  btnMostrarSenha.addEventListener(
    "click",
    function () {

      alternarVisibilidadeSenha(
        senhaInput,
        btnMostrarSenha
      );

    }
  );


  // Mostrar / esconder confirmação

  btnMostrarConfirmacao.addEventListener(
    "click",
    function () {

      alternarVisibilidadeSenha(
        confirmarSenhaInput,
        btnMostrarConfirmacao
      );

    }
  );


  formCadastro.addEventListener(
    "submit",
    function (event) {

      event.preventDefault();


      // Se apertar Enter na etapa 1,
      // tenta avançar para a etapa 2.

      if (etapaAtual === 1) {

        irParaEtapaSenha();

        return;

      }


      // Valida os campos de senha.

      const senhaValida =
        validarSenha();

      const confirmacaoValida =
        validarConfirmacaoSenha();


      if (
        !senhaValida ||
        !confirmacaoValida
      ) {

        if (!senhaValida) {

          senhaInput.focus();

        } else {

          confirmarSenhaInput.focus();

        }

        return;

      }


      // Dados prontos para integração
      // com o backend Java.

      const dadosCadastro = {

        nome:
          nomeInput.value.trim(),

        email:
          emailInput.value
            .trim()
            .toLowerCase(),

        celular:
          celularInput.value,

        senha:
          senhaInput.value

      };


      /*
        FUTURAMENTE:

        Enviar dadosCadastro via HTTPS
        para a API Java.

        O backend deverá:

        1. Validar os dados novamente.
        2. Verificar se o e-mail já existe.
        3. Gerar o hash seguro da senha.
        4. Salvar o usuário no MongoDB.

        IMPORTANTE:

        Nunca salvar a senha em texto
        puro no banco de dados.

        Nunca armazenar a senha no
        localStorage ou exibi-la no console.
      */


      alert(
        "Dados validados! Falta conectar o cadastro ao backend para salvar o usuário."
      );

    }
  );

});
