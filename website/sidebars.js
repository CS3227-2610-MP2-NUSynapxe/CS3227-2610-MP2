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
    {
      type: 'category',
      label: 'Developer Guide',
      link: {
        type: 'doc',
        id: 'docs/DeveloperGuide',
      },
      collapsed: false,
      items: [
        'docs/ProductSpecifications',
        'docs/ArchitectureAndDesign',
        'docs/TestingStrategy',
      ],
    },
  ],
};

export default sidebars;
