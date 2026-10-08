(function () {
  var theme = null;
  try {
    theme = localStorage.getItem("theme");
  } catch (e) {
    theme = null;
  }
  var dark = theme === "dark" || (theme !== "light" && matchMedia("(prefers-color-scheme: dark)").matches);
  if (dark) document.documentElement.classList.add("dark");
  var locale = location.pathname.split("/")[1];
  if (locale === "fr" || locale === "en" || locale === "nl") document.documentElement.lang = locale;
})();
