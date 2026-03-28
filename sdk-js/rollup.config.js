import typescript from '@rollup/plugin-typescript';
import terser from '@rollup/plugin-terser';

const banner = `/*!
 * @cosmos/fraud-sdk-js v1.0.0
 * Cosmos Fraud Detection JavaScript SDK
 * (c) ${new Date().getFullYear()} Cosmos
 * Released under the MIT License.
 */`;

export default [
  // UMD build
  {
    input: 'src/index.ts',
    output: {
      file: 'dist/fraud-sdk.umd.js',
      format: 'umd',
      name: 'FraudSDK',
      banner,
      sourcemap: true,
    },
    plugins: [
      typescript({
        tsconfig: './tsconfig.json',
        declaration: false,
        declarationDir: undefined,
      }),
      terser({
        format: {
          comments: /^!/,
        },
      }),
    ],
  },
  // ESM build
  {
    input: 'src/index.ts',
    output: {
      file: 'dist/fraud-sdk.esm.js',
      format: 'esm',
      banner,
      sourcemap: true,
    },
    plugins: [
      typescript({
        tsconfig: './tsconfig.json',
        declaration: true,
        declarationDir: 'dist',
      }),
      terser({
        format: {
          comments: /^!/,
        },
      }),
    ],
  },
];
