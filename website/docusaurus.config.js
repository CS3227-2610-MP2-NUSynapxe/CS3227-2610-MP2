import {themes as prismThemes} from 'prism-react-renderer';

const repositoryName = process.env.GITHUB_REPOSITORY?.split('/')[1];
const isGitHubActions = process.env.GITHUB_ACTIONS === 'true';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'NUSynapxe',
  tagline: 'Java 25 desktop application documentation',
  url: process.env.DOCUSAURUS_URL ?? 'http://localhost',
  baseUrl: isGitHubActions && repositoryName ? `/${repositoryName}/` : '/',
  organizationName: process.env.GITHUB_REPOSITORY_OWNER ?? 'johnwz123',
  projectName: repositoryName ?? 'CS3227-2610-MP2',
  onBrokenLinks: 'throw',
  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },
  themes: ['@docusaurus/theme-mermaid'],
  presets: [
    [
      'classic',
      {
        docs: {
          path: '..',
          include: ['README.md', 'docs/**/*.md'],
          routeBasePath: '/',
          sidebarPath: './sidebars.js',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      },
    ],
  ],
  themeConfig: {
    navbar: {
      title: 'NUSynapxe',
      items: [
        {type: 'docSidebar', sidebarId: 'guides', position: 'left', label: 'Guides'},
        {
          href: `https://github.com/${
            process.env.GITHUB_REPOSITORY ?? 'johnwz123/CS3227-2610-MP2'
          }`,
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Guides',
          items: [
            {label: 'Overview', to: '/'},
            {label: 'Developer Guide', to: '/docs/DeveloperGuide'},
            {label: 'User Guide', to: '/docs/UserGuide'},
          ],
        },
      ],
      copyright: `Copyright ${new Date().getFullYear()} NUSynapxe.`,
    },
    prism: {
      additionalLanguages: ['gherkin'],
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  },
};

export default config;
