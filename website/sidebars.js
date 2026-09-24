/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  guides: [
    'docs/overview',
    {
      type: 'category',
      label: 'User Guide',
      link: {
        type: 'doc',
        id: 'docs/UserGuide',
      },
      collapsed: false,
      items: [
        'docs/ReceptionistGuide',
        'docs/DoctorGuide',
      ],
    },
    'docs/DeveloperGuide',
  ],
};

export default sidebars;
