/* =========================================
   DADOS TEMPORÁRIOS
========================================= */

const STORAGE_KEY = "psicomManagerUsuario";


const usuarioPadrao = {
  nome: "Fulano da Silva",
  email: "fulano@gmail.com",
  telefone: "19999994444",
  cargo: "Estoquista",
  foto: null
};


let usuario = carregarUsuario();


/* =========================================
   ELEMENTOS GERAIS
========================================= */

const nomeUsuario =
  document.getElementById("nomeUsuario");

const emailUsuario =
  document.getElementById("emailUsuario");

const telefoneUsuario =
  document.getElementById("telefoneUsuario");

const cargoUsuario =
  document.getElementById("cargoUsuario");


/* =========================================
   ELEMENTOS DO E-MAIL
========================================= */

const visualizacaoEmail =
  document.getElementById("visualizacaoEmail");

const edicaoEmail =
  document.getElementById("edicaoEmail");

const inputEmailUsuario =
  document.getElementById("inputEmailUsuario");

const erroEmailUsuario =
  document.getElementById("erroEmailUsuario");

const btnEditarEmail =
  document.getElementById("btnEditarEmail");

const btnSalvarEmail =
  document.getElementById("btnSalvarEmail");

const btnCancelarEmail =
  document.getElementById("btnCancelarEmail");


/* =========================================
   ELEMENTOS DO TELEFONE
========================================= */

const visualizacaoTelefone =
  document.getElementById("visualizacaoTelefone");

const edicaoTelefone =
  document.getElementById("edicaoTelefone");

const inputTelefoneUsuario =
  document.getElementById("inputTelefoneUsuario");

const erroTelefoneUsuario =
  document.getElementById("erroTelefoneUsuario");

const btnEditarTelefone =
  document.getElementById("btnEditarTelefone");

const btnSalvarTelefone =
  document.getElementById("btnSalvarTelefone");

const btnCancelarTelefone =
  document.getElementById("btnCancelarTelefone");


/* =========================================
   ELEMENTOS DA FOTO
========================================= */

const fotoUsuario =
  document.getElementById("fotoUsuario");

const fotoPlaceholder =
  document.getElementById("fotoPlaceholder");

const btnAlterarFoto =
  document.getElementById("btnAlterarFoto");

const inputFotoUsuario =
  document.getElementById("inputFotoUsuario");

const erroFotoUsuario =
  document.getElementById("erroFotoUsuario");


/* =========================================
   ELEMENTOS DA SENHA
========================================= */

const modalAlterarSenha =
  document.getElementById("modalAlterarSenha");

const btnAlterarSenha =
  document.getElementById("btnAlterarSenha");

const novaSenha =
  document.getElementById("novaSenha");

const confirmarNovaSenha =
  document.getElementById("confirmarNovaSenha");

const erroSenhaUsuario =
  document.getElementById("erroSenhaUsuario");

const btnMostrarNovaSenha =
  document.getElementById("btnMostrarNovaSenha");

const btnMostrarConfirmacaoSenha =
  document.getElementById("btnMostrarConfirmacaoSenha");

const btnSalvarSenha =
  document.getElementById("btnSalvarSenha");

const btnCancelarSenha =
  document.getElementById("btnCancelarSenha");


/* =========================================
   LOCAL STORAGE
========================================= */

function carregarUsuario() {

  const dadosSalvos =
    localStorage.getItem(STORAGE_KEY);


  if (!dadosSalvos) {

    return {
      ...usuarioPadrao
    };

  }


  try {

    return {
      ...usuarioPadrao,
      ...JSON.parse(dadosSalvos)
    };

  } catch (error) {

    console.error(
      "Erro ao carregar usuário:",
      error
    );


    return {
      ...usuarioPadrao
    };

  }

}


function salvarUsuario() {

  try {

    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(usuario)
    );


    return true;

  } catch (error) {

    console.error(
      "Erro ao salvar usuário:",
      error
    );


    return false;

  }

}


/* =========================================
   ATUALIZA A TELA
========================================= */

function atualizarTela() {

  nomeUsuario.textContent =
    usuario.nome;


  emailUsuario.textContent =
    usuario.email;


  telefoneUsuario.textContent =
    formatarTelefone(usuario.telefone);


  cargoUsuario.textContent =
    usuario.cargo;


  if (usuario.foto) {

    fotoUsuario.src =
      usuario.foto;


    fotoUsuario.classList.remove(
      "hidden"
    );


    fotoPlaceholder.classList.add(
      "hidden"
    );

  } else {

    fotoUsuario.src = "";


    fotoUsuario.classList.add(
      "hidden"
    );


    fotoPlaceholder.classList.remove(
      "hidden"
    );

  }

}


atualizarTela();


/* =========================================
   EDITAR E-MAIL
========================================= */

btnEditarEmail.addEventListener(
  "click",
  function () {

    fecharEdicaoTelefone();


    inputEmailUsuario.value =
      usuario.email;


    removerErroEmail();


    visualizacaoEmail.classList.add(
      "hidden"
    );


    edicaoEmail.classList.remove(
      "hidden"
    );


    inputEmailUsuario.focus();
    inputEmailUsuario.select();

  }
);


btnSalvarEmail.addEventListener(
  "click",
  salvarEmail
);


btnCancelarEmail.addEventListener(
  "click",
  fecharEdicaoEmail
);


inputEmailUsuario.addEventListener(
  "input",
  function () {

    if (
      !erroEmailUsuario.classList.contains(
        "hidden"
      )
    ) {

      validarEmailDigitado();

    }

  }
);


inputEmailUsuario.addEventListener(
  "keydown",
  function (event) {

    if (event.key === "Enter") {

      salvarEmail();

    }


    if (event.key === "Escape") {

      fecharEdicaoEmail();

    }

  }
);


function salvarEmail() {

  const email =
    inputEmailUsuario.value
      .trim()
      .toLowerCase();


  if (!validarEmailDigitado()) {

    return;

  }


  usuario.email =
    email;


  salvarUsuario();

  atualizarTela();

  fecharEdicaoEmail();

}


function validarEmailDigitado() {

  const email =
    inputEmailUsuario.value.trim();


  if (email === "") {

    mostrarErroEmail(
      "Informe um e-mail."
    );


    return false;

  }


  if (
    inputEmailUsuario.validity.typeMismatch
  ) {

    mostrarErroEmail(
      "Digite um e-mail válido."
    );


    return false;

  }


  removerErroEmail();


  return true;

}


function mostrarErroEmail(mensagem) {

  erroEmailUsuario.textContent =
    mensagem;


  erroEmailUsuario.classList.remove(
    "hidden"
  );


  inputEmailUsuario.setAttribute(
    "aria-invalid",
    "true"
  );

}


function removerErroEmail() {

  erroEmailUsuario.textContent =
    "";


  erroEmailUsuario.classList.add(
    "hidden"
  );


  inputEmailUsuario.removeAttribute(
    "aria-invalid"
  );

}


function fecharEdicaoEmail() {

  removerErroEmail();


  edicaoEmail.classList.add(
    "hidden"
  );


  visualizacaoEmail.classList.remove(
    "hidden"
  );

}


/* =========================================
   EDITAR TELEFONE
========================================= */

btnEditarTelefone.addEventListener(
  "click",
  function () {

    fecharEdicaoEmail();


    inputTelefoneUsuario.value =
      formatarTelefone(
        usuario.telefone
      );


    removerErroTelefone();


    visualizacaoTelefone.classList.add(
      "hidden"
    );


    edicaoTelefone.classList.remove(
      "hidden"
    );


    inputTelefoneUsuario.focus();
    inputTelefoneUsuario.select();

  }
);


btnSalvarTelefone.addEventListener(
  "click",
  salvarTelefone
);


btnCancelarTelefone.addEventListener(
  "click",
  fecharEdicaoTelefone
);


inputTelefoneUsuario.addEventListener(
  "input",
  function () {

    inputTelefoneUsuario.value =
      aplicarMascaraTelefone(
        inputTelefoneUsuario.value
      );


    if (
      !erroTelefoneUsuario.classList.contains(
        "hidden"
      )
    ) {

      validarTelefoneDigitado();

    }

  }
);


inputTelefoneUsuario.addEventListener(
  "keydown",
  function (event) {

    if (event.key === "Enter") {

      salvarTelefone();

    }


    if (event.key === "Escape") {

      fecharEdicaoTelefone();

    }

  }
);


function salvarTelefone() {

  if (!validarTelefoneDigitado()) {

    return;

  }


  usuario.telefone =
    obterNumerosTelefone(
      inputTelefoneUsuario.value
    );


  salvarUsuario();

  atualizarTela();

  fecharEdicaoTelefone();

}


function validarTelefoneDigitado() {

  const numeros =
    obterNumerosTelefone(
      inputTelefoneUsuario.value
    );


  if (numeros.length === 0) {

    mostrarErroTelefone(
      "Informe um telefone."
    );


    return false;

  }


  if (numeros.length !== 11) {

    mostrarErroTelefone(
      "Digite um celular válido com DDD."
    );


    return false;

  }


  removerErroTelefone();


  return true;

}


function obterNumerosTelefone(valor) {

  let numeros =
    String(valor).replace(
      /\D/g,
      ""
    );


  if (
    numeros.length > 11 &&
    numeros.startsWith("55")
  ) {

    numeros =
      numeros.substring(2);

  }


  return numeros.substring(
    0,
    11
  );

}


function aplicarMascaraTelefone(valor) {

  const numeros =
    obterNumerosTelefone(valor);


  if (numeros.length === 0) {

    return "";

  }


  if (numeros.length <= 2) {

    return `(${numeros}`;

  }


  if (numeros.length <= 7) {

    return (
      `(${numeros.substring(0, 2)}) ` +
      numeros.substring(2)
    );

  }


  return (
    `(${numeros.substring(0, 2)}) ` +
    `${numeros.substring(2, 7)}-` +
    numeros.substring(7, 11)
  );

}


function formatarTelefone(valor) {

  const numeros =
    obterNumerosTelefone(valor);


  if (numeros.length !== 11) {

    return valor;

  }


  return (
    `(${numeros.substring(0, 2)}) ` +
    `${numeros.substring(2, 7)}-` +
    numeros.substring(7, 11)
  );

}


function mostrarErroTelefone(mensagem) {

  erroTelefoneUsuario.textContent =
    mensagem;


  erroTelefoneUsuario.classList.remove(
    "hidden"
  );


  inputTelefoneUsuario.setAttribute(
    "aria-invalid",
    "true"
  );

}


function removerErroTelefone() {

  erroTelefoneUsuario.textContent =
    "";


  erroTelefoneUsuario.classList.add(
    "hidden"
  );


  inputTelefoneUsuario.removeAttribute(
    "aria-invalid"
  );

}


function fecharEdicaoTelefone() {

  removerErroTelefone();


  edicaoTelefone.classList.add(
    "hidden"
  );


  visualizacaoTelefone.classList.remove(
    "hidden"
  );

}


/* =========================================
   ALTERAR SENHA
========================================= */

btnAlterarSenha.addEventListener(
  "click",
  function () {

    removerErroSenha();


    modalAlterarSenha.classList.remove(
      "hidden"
    );


    modalAlterarSenha.classList.add(
      "flex"
    );


    novaSenha.focus();

  }
);


btnCancelarSenha.addEventListener(
  "click",
  fecharModalSenha
);


btnSalvarSenha.addEventListener(
  "click",
  salvarNovaSenha
);


function salvarNovaSenha() {

  const senha =
    novaSenha.value;


  const confirmacao =
    confirmarNovaSenha.value;


  if (senha === "") {

    mostrarErroSenha(
      "Informe a nova senha."
    );


    return;

  }


  if (senha.length < 8) {

    mostrarErroSenha(
      "A senha deve possuir pelo menos 8 caracteres."
    );


    return;

  }


  if (confirmacao === "") {

    mostrarErroSenha(
      "Confirme a nova senha."
    );


    return;

  }


  if (senha !== confirmacao) {

    mostrarErroSenha(
      "As senhas não coincidem."
    );


    return;

  }


  /*
    FUTURAMENTE:

    A nova senha será enviada para
    o backend Java.

    Não devemos armazenar senha
    no localStorage.
  */

  console.log(
    "Nova senha válida."
  );


  fecharModalSenha();

}


function mostrarErroSenha(mensagem) {

  erroSenhaUsuario.textContent =
    mensagem;


  erroSenhaUsuario.classList.remove(
    "hidden"
  );

}


function removerErroSenha() {

  erroSenhaUsuario.textContent =
    "";


  erroSenhaUsuario.classList.add(
    "hidden"
  );

}


function fecharModalSenha() {

  novaSenha.value =
    "";


  confirmarNovaSenha.value =
    "";


  novaSenha.type =
    "password";


  confirmarNovaSenha.type =
    "password";


  btnMostrarNovaSenha.setAttribute(
    "aria-label",
    "Mostrar senha"
  );


  btnMostrarConfirmacaoSenha.setAttribute(
    "aria-label",
    "Mostrar senha"
  );


  removerErroSenha();


  modalAlterarSenha.classList.add(
    "hidden"
  );


  modalAlterarSenha.classList.remove(
    "flex"
  );

}


/* =========================================
   MOSTRAR / ESCONDER SENHA
========================================= */

function alternarVisibilidadeSenha(
  input,
  botao
) {

  const estaVisivel =
    input.type === "text";


  if (estaVisivel) {

    input.type =
      "password";


    botao.setAttribute(
      "aria-label",
      "Mostrar senha"
    );

  } else {

    input.type =
      "text";


    botao.setAttribute(
      "aria-label",
      "Ocultar senha"
    );

  }

}


btnMostrarNovaSenha.addEventListener(
  "click",
  function () {

    alternarVisibilidadeSenha(
      novaSenha,
      btnMostrarNovaSenha
    );

  }
);


btnMostrarConfirmacaoSenha.addEventListener(
  "click",
  function () {

    alternarVisibilidadeSenha(
      confirmarNovaSenha,
      btnMostrarConfirmacaoSenha
    );

  }
);


/* =========================================
   FECHAR MODAL COM ESC
========================================= */

document.addEventListener(
  "keydown",
  function (event) {

    if (
      event.key === "Escape" &&
      !modalAlterarSenha.classList.contains(
        "hidden"
      )
    ) {

      fecharModalSenha();

    }

  }
);


/* =========================================
   ALTERAR FOTO
========================================= */

btnAlterarFoto.addEventListener(
  "click",
  function () {

    inputFotoUsuario.click();

  }
);


inputFotoUsuario.addEventListener(
  "change",
  function () {

    removerErroFoto();


    const arquivo =
      inputFotoUsuario.files[0];


    if (!arquivo) {

      return;

    }


    const tiposPermitidos = [
      "image/jpeg",
      "image/png",
      "image/webp"
    ];


    if (
      !tiposPermitidos.includes(
        arquivo.type
      )
    ) {

      mostrarErroFoto(
        "Escolha uma imagem JPG, PNG ou WEBP."
      );


      inputFotoUsuario.value =
        "";


      return;

    }


    const tamanhoMaximo =
      2 * 1024 * 1024;


    if (
      arquivo.size > tamanhoMaximo
    ) {

      mostrarErroFoto(
        "A imagem deve ter no máximo 2 MB."
      );


      inputFotoUsuario.value =
        "";


      return;

    }


    const leitor =
      new FileReader();


    leitor.addEventListener(
      "load",
      function () {

        const fotoAnterior =
          usuario.foto;


        usuario.foto =
          leitor.result;


        if (!salvarUsuario()) {

          usuario.foto =
            fotoAnterior;


          mostrarErroFoto(
            "Não foi possível salvar a foto no navegador."
          );


          return;

        }


        atualizarTela();

      }
    );


    leitor.readAsDataURL(
      arquivo
    );

  }
);


function mostrarErroFoto(mensagem) {

  erroFotoUsuario.textContent =
    mensagem;


  erroFotoUsuario.classList.remove(
    "hidden"
  );

}


function removerErroFoto() {

  erroFotoUsuario.textContent =
    "";


  erroFotoUsuario.classList.add(
    "hidden"
  );

}