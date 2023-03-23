const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO NIO",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "end-of-stream-handling",
        "blocking",
        "files",
        "sockets",
        "resources",
        "charsets",
        "use-cases"
      ]
    }
  ]
};

module.exports = sidebars;
