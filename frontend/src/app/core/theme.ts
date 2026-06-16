import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

export const HotelPreset = definePreset(Aura, {
    semantic: {
        primary: {
            50: '#f0f5ff',
            100: '#dbeafe', // Primary Light
            200: '#bed7fe',
            300: '#93c5fd',
            400: '#60a5fa',
            500: '#2563eb', // Primary 500
            600: '#1d4ed8', // Primary Hover
            700: '#1e40af',
            800: '#1e3a8a',
            900: '#1e3a8a',
            950: '#172554'
        },
        colorScheme: {
            light: {
                primary: {
                    color: '{primary.500}',
                    inverseColor: '#ffffff',
                    hoverColor: '{primary.600}',
                    activeColor: '{primary.700}'
                },
                highlight: {
                    background: '{primary.100}',
                    focusBackground: '{primary.200}',
                    color: '{primary.700}',
                    focusColor: '{primary.800}'
                }
            },
            dark: {
                primary: {
                    color: '{primary.400}',
                    inverseColor: '{surface.900}',
                    hoverColor: '{primary.300}',
                    activeColor: '{primary.200}'
                },
                highlight: {
                    background: 'rgba(250, 250, 250, .16)',
                    focusBackground: 'rgba(250, 250, 250, .24)',
                    color: 'rgba(255,255,255,.87)',
                    focusColor: 'rgba(255,255,255,.87)'
                }
            }
        }
    }
});
