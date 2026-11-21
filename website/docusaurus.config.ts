import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
    title: 'javafmt',

    url: 'https://javafmt.io',
    baseUrl: '/',

    trailingSlash: false,

    onBrokenLinks: 'throw',
    onBrokenAnchors: 'throw',

    markdown: {
        hooks: {
            onBrokenMarkdownLinks: 'throw',
        },
    },

    presets: [
        [
            'classic',
            {
                docs: {
                    path: '../docs',
                    exclude: ['superpowers/**'],
                    editUrl: 'https://github.com/javafmt-io/javafmt/edit/main/docs/',
                },
                blog: false,
                theme: {
                    customCss: './src/css/custom.css',
                },
            } satisfies Preset.Options,
        ],
    ],

    themeConfig: {
        navbar: {
            title: 'javafmt',
            items: [
                {
                    type: 'docSidebar',
                    sidebarId: 'defaultSidebar',
                    position: 'left',
                    label: 'Docs',
                },
                {
                    href: 'https://github.com/javafmt-io/javafmt',
                    label: 'GitHub',
                    position: 'right',
                },
            ],
        },
        footer: {
            style: 'dark',
            links: [
                {
                    title: 'Docs',
                    items: [
                        {label: 'Getting Started', to: '/docs/intro'},
                    ],
                },
                {
                    title: 'More',
                    items: [
                        {label: 'GitHub', href: 'https://github.com/javafmt-io/javafmt'},
                        {label: 'Maven Central', href: 'https://central.sonatype.com/namespace/io.javafmt'},
                    ],
                },
            ],
            copyright: `Copyright © ${new Date().getFullYear()} Jim Schneidereit. Built with Docusaurus.`,
        },
        prism: {
            theme: prismThemes.github,
            darkTheme: prismThemes.dracula,
            additionalLanguages: ['java', 'kotlin', 'bash', 'properties'],
        },
    } satisfies Preset.ThemeConfig,
};

export default config;
