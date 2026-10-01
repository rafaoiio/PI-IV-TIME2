async function loadPartial(partialUrl, placeholderId) {
  try {
    const placeholder = document.getElementById(placeholderId);
    placeholder.innerHTML = '<div class="loading">Loading...</div>';

    const response = await fetch(partialUrl);
    if (!response.ok) throw new Error(`HTTP error! Status: ${response.status}`);

    const partialContent = await response.text();
    placeholder.innerHTML = partialContent;
  } catch (error) {
    console.error(`Failed to load partial: ${error.message}`);
    document.getElementById(placeholderId).innerHTML =
      '<div class="error">Failed to load content.</div>';
  }
}

// utilizo a função criada para
document.addEventListener("DOMContentLoaded", () => {
  loadPartial("/partial/header.html", "header-placeholder");
  loadPartial("/partial/footer.html", "footer-placeholder");
  loadPartial("/partial/sidebar.html", "aside-placeholder");
});

document.addEventListener("click", (event) => {
  const toggle = event.target.closest("[data-sidebar-toggle]");
  if (!toggle) return;

  const submenu = document.getElementById(toggle.getAttribute("aria-controls"));
  if (!submenu) return;

  const expanded = toggle.getAttribute("aria-expanded") === "true";

  toggle.setAttribute("aria-expanded", String(!expanded));
  submenu.classList.toggle("hidden", expanded);

  toggle.querySelector("[data-sidebar-chevron]").style.rotate = expanded
    ? "0deg"
    : "-90deg";
});
