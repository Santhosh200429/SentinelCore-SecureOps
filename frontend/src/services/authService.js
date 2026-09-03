/**
 * authService.js
 *
 * Purpose : Handles login (Spring Security form-login), logout, and registration.
 *
 * IMPORTANT:
 * - Normal REST APIs use axiosInstance → /api/...
 * - Spring Security login/logout use authAxios → root backend URL
 *
 * This allows VITE_API_URL to remain:
 *
 *   https://sentinelcore-secureops-o5wr.onrender.com/api
 *
 * while authentication correctly goes to:
 *
 *   https://sentinelcore-secureops-o5wr.onrender.com/login
 *   https://sentinelcore-secureops-o5wr.onrender.com/logout
 */

import axiosInstance, { authAxios } from '../api/axios.js';

const authService = {

    // ─────────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────────

    login: async (username, password, rememberMe = false) => {

        const body = new URLSearchParams();

        body.append('username', username);
        body.append('password', password);

        if (rememberMe) {
            body.append('remember-me', 'on');
        }

        try {

            /*
             * IMPORTANT:
             *
             * Use authAxios instead of axiosInstance.
             *
             * axiosInstance baseURL:
             *   .../api
             *
             * authAxios baseURL:
             *   .../
             *
             * Therefore this request becomes:
             *
             *   POST /login
             *
             * NOT:
             *
             *   POST /api/login
             */

            const response = await authAxios.post(
                '/login',
                body,
                {
                    withCredentials: true,

                    headers: {
                        'Content-Type':
                            'application/x-www-form-urlencoded',

                        'Accept':
                            'application/json'
                    },

                    /*
                     * We handle HTTP errors ourselves below.
                     */
                    validateStatus: () => true,
                }
            );

            // ─────────────────────────────────────────────────────────────────
            // Authentication failed
            // ─────────────────────────────────────────────────────────────────

            if (response.status === 401) {

                const err =
                    new Error('Bad credentials');

                err.response = {
                    status: 401,
                };

                throw err;
            }

            // ─────────────────────────────────────────────────────────────────
            // Forbidden
            // ─────────────────────────────────────────────────────────────────

            if (response.status === 403) {

                const err =
                    new Error('Forbidden');

                err.response = {
                    status: 403,
                };

                throw err;
            }

            // ─────────────────────────────────────────────────────────────────
            // Other HTTP errors
            // ─────────────────────────────────────────────────────────────────

            if (response.status >= 400) {

                const err =
                    new Error('Bad request');

                err.response = {
                    status: response.status,
                };

                throw err;
            }

            return response;

        } catch (err) {

            if (err.response) {
                throw err;
            }

            console.error(
                '[authService] Login network/request execution error:',
                err.message
            );

            throw err;
        }
    },


    // ─────────────────────────────────────────────────────────────────────────
    // LOGOUT
    // ─────────────────────────────────────────────────────────────────────────

    logout: () => {

        const csrf =
            document.cookie
                .match(/XSRF-TOKEN=([^;]+)/)?.[1];

        const body =
            new URLSearchParams();

        if (csrf) {
            body.append(
                '_csrf',
                decodeURIComponent(csrf)
            );
        }

        /*
         * IMPORTANT:
         *
         * Logout is a Spring Security endpoint:
         *
         *   /logout
         *
         * Therefore use authAxios.
         */

        return authAxios.post(
            '/logout',
            body,
            {
                withCredentials: true,

                headers: {
                    'Content-Type':
                        'application/x-www-form-urlencoded'
                },

                validateStatus: () => true,
            }
        );
    },


    // ─────────────────────────────────────────────────────────────────────────
    // REGISTRATION
    // ─────────────────────────────────────────────────────────────────────────

    register: (data) => {

        /*
         * Registration is a REST API endpoint:
         *
         *   /api/users/register
         *
         * Therefore continue using axiosInstance.
         */

        return axiosInstance.post(
            '/users/register',
            data,
            {
                withCredentials: true,

                headers: {
                    'Content-Type':
                        'application/json',
                },
            }
        );
    },
};

export default authService;