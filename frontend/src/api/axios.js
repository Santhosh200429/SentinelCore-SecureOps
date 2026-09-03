/**
 * SentinelCore SecureOps — Central Axios Instance
 *
 * Purpose:
 *   Creates the shared HTTP clients used by all service modules.
 *
 * NORMAL API CLIENT:
 *   Uses VITE_API_URL.
 *
 *   If VITE_API_URL is:
 *
 *       https://sentinelcore-secureops-o5wr.onrender.com/api
 *
 *   then normal API requests become:
 *
 *       https://sentinelcore-secureops-o5wr.onrender.com/api/...
 *
 * AUTH CLIENT:
 *   Spring Security login/logout endpoints are NOT under /api.
 *
 *       https://sentinelcore-secureops-o5wr.onrender.com/login
 *       https://sentinelcore-secureops-o5wr.onrender.com/logout
 *
 * IMPORTANT:
 *   Some existing service files use paths such as:
 *
 *       /api/dashboard/user
 *       /api/incidents
 *       /api/alerts/live
 *
 *   Since VITE_API_URL already contains /api, this file removes the
 *   duplicate /api from the request path.
 *
 *   Example:
 *
 *       baseURL = https://...onrender.com/api
 *       request  = /api/dashboard/user
 *
 *   becomes:
 *
 *       https://...onrender.com/api/dashboard/user
 *
 *   instead of:
 *
 *       https://...onrender.com/api/api/dashboard/user
 *
 * Features:
 *   - withCredentials keeps JSESSIONID alive
 *   - CSRF interceptor reads XSRF-TOKEN
 *   - Normal API 401/403 handling is preserved
 *   - Authentication requests use a separate client
 *   - Failed login does not trigger session-expired redirect
 *   - Prevents duplicate /api/api paths
 */

import axios from 'axios';


// ─────────────────────────────────────────────────────────────────────────────
// CSRF TOKEN HELPER
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reads the XSRF-TOKEN cookie set by Spring Security's CsrfFilter.
 *
 * @returns {string|null}
 */
function getCsrfToken() {

    const match =
        document.cookie.match(/XSRF-TOKEN=([^;]+)/);

    return match
        ? decodeURIComponent(match[1])
        : null;
}


// ─────────────────────────────────────────────────────────────────────────────
// API BASE URL
// ─────────────────────────────────────────────────────────────────────────────
//
// Vercel environment variable:
//
// VITE_API_URL=https://sentinelcore-secureops-o5wr.onrender.com/api
//
// Local development can leave VITE_API_URL empty so that Vite's
// development proxy can handle /api/* requests.
//

const API_BASE =
    import.meta.env.VITE_API_URL || '';


// ─────────────────────────────────────────────────────────────────────────────
// AUTH BASE URL
// ─────────────────────────────────────────────────────────────────────────────
//
// Spring Security authentication endpoints are:
//
//     /login
//     /logout
//
// They are NOT:
//
//     /api/login
//     /api/logout
//
// Therefore remove the trailing /api from API_BASE.
//

const AUTH_BASE =
    API_BASE.endsWith('/api')
        ? API_BASE.slice(0, -4)
        : API_BASE;


// ─────────────────────────────────────────────────────────────────────────────
// DEBUG LOGS
// ─────────────────────────────────────────────────────────────────────────────

console.log(
    'VITE_API_URL =',
    import.meta.env.VITE_API_URL
);

console.log(
    'API_BASE =',
    API_BASE
);

console.log(
    'AUTH_BASE =',
    AUTH_BASE
);


// ─────────────────────────────────────────────────────────────────────────────
// NORMAL API CLIENT
// ─────────────────────────────────────────────────────────────────────────────
//
// Used by services such as:
//
//     dashboardService
//     incidentService
//     alertService
//     assetService
//     userService
//     vulnerabilityService
//     etc.
//
// The base URL already contains /api.
//

const axiosInstance = axios.create({

    baseURL: API_BASE,

    // Required so browser sends the JSESSIONID session cookie
    // to the Render backend.
    withCredentials: true,

    headers: {
        'Content-Type': 'application/json',
    },
});


// ─────────────────────────────────────────────────────────────────────────────
// AUTHENTICATION CLIENT
// ─────────────────────────────────────────────────────────────────────────────
//
// Used for:
//
//     POST /login
//     POST /logout
//
// This client intentionally does NOT use API_BASE.
//

const authAxios = axios.create({

    baseURL: AUTH_BASE,

    // Required for JSESSIONID authentication.
    withCredentials: true,

    headers: {
        'Content-Type': 'application/json',
    },
});


// ─────────────────────────────────────────────────────────────────────────────
// NORMAL API REQUEST INTERCEPTOR
// ─────────────────────────────────────────────────────────────────────────────
//
// IMPORTANT FIX:
//
// Existing service files may contain:
//
//     axiosInstance.get('/api/dashboard/user')
//
// But API_BASE already contains:
//
//     /api
//
// Without normalization:
//
//     /api + /api/dashboard/user
//
// becomes:
//
//     /api/api/dashboard/user
//
// Therefore remove the first /api from request paths.
//

axiosInstance.interceptors.request.use(

    (config) => {

        // ─────────────────────────────────────────────────────────────
        // Prevent duplicate /api/api paths
        // ─────────────────────────────────────────────────────────────
        //
        // Example:
        //
        // config.url:
        //     /api/dashboard/user
        //
        // becomes:
        //
        //     /dashboard/user
        //
        // Axios then combines it with:
        //
        //     https://...onrender.com/api
        //
        // Result:
        //
        //     https://...onrender.com/api/dashboard/user
        //

        if (
            config.url &&
            config.url.startsWith('/api/')
        ) {

            config.url =
                config.url.substring(4);
        }


        // ─────────────────────────────────────────────────────────────
        // CSRF
        // ─────────────────────────────────────────────────────────────

        const mutatingMethods = [
            'post',
            'put',
            'delete',
            'patch',
        ];

        const method =
            config.method?.toLowerCase();

        if (
            mutatingMethods.includes(method)
        ) {

            const token =
                getCsrfToken();

            if (token) {

                config.headers =
                    config.headers || {};

                config.headers['X-XSRF-TOKEN'] =
                    token;
            }
        }


        return config;
    },

    (error) => {

        return Promise.reject(error);
    }
);


// ─────────────────────────────────────────────────────────────────────────────
// AUTH REQUEST INTERCEPTOR
// ─────────────────────────────────────────────────────────────────────────────
//
// Login/logout are currently excluded from CSRF protection in the backend.
//
// We still attach the token when available so the client remains compatible
// if CSRF protection is enabled for these endpoints later.
//

authAxios.interceptors.request.use(

    (config) => {

        const mutatingMethods = [
            'post',
            'put',
            'delete',
            'patch',
        ];

        const method =
            config.method?.toLowerCase();

        if (
            mutatingMethods.includes(method)
        ) {

            const token =
                getCsrfToken();

            if (token) {

                config.headers =
                    config.headers || {};

                config.headers['X-XSRF-TOKEN'] =
                    token;
            }
        }

        return config;
    },

    (error) => {

        return Promise.reject(error);
    }
);


// ─────────────────────────────────────────────────────────────────────────────
// NORMAL API RESPONSE INTERCEPTOR
// ─────────────────────────────────────────────────────────────────────────────
//
// Handles:
//
//     401 Unauthorized
//     403 Forbidden
//
// IMPORTANT:
//
// authAxios does NOT use this interceptor.
//
// Therefore:
//
//     POST /login
//
// returning 401 will NOT redirect the browser to:
//
//     /login?expired
//
// Only normal authenticated API requests use the session-expiration logic.
//

axiosInstance.interceptors.response.use(

    (response) => {

        return response;
    },

    (error) => {

        // ─────────────────────────────────────────────────────────────
        // 401 — UNAUTHORIZED
        // ─────────────────────────────────────────────────────────────

        if (
            error.response?.status === 401
        ) {

            const currentPath =
                window.location.pathname;

            const requestUrl =
                error.config?.url || '';


            // ─────────────────────────────────────────────────────────
            // Check whether user is already on login page
            // ─────────────────────────────────────────────────────────

            const isLoginPage =
                currentPath === '/login' ||
                currentPath.startsWith('/login');


            // ─────────────────────────────────────────────────────────
            // Check initial session request
            // ─────────────────────────────────────────────────────────
            //
            // After the request interceptor runs, a request that was:
            //
            //     /api/dashboard/user
            //
            // becomes:
            //
            //     /dashboard/user
            //
            // Therefore check for /dashboard/user.
            //

            const isSessionCheck =
                requestUrl.includes('/dashboard/user');


            // ─────────────────────────────────────────────────────────
            // Redirect only when appropriate
            // ─────────────────────────────────────────────────────────

            if (
                !isLoginPage &&
                !isSessionCheck
            ) {

                window.location.href =
                    '/login?expired';
            }
        }


        // ─────────────────────────────────────────────────────────────
        // 403 — FORBIDDEN
        // ─────────────────────────────────────────────────────────────

        else if (
            error.response?.status === 403
        ) {

            window.location.href =
                '/403';
        }


        return Promise.reject(error);
    }
);


// ─────────────────────────────────────────────────────────────────────────────
// EXPORTS
// ─────────────────────────────────────────────────────────────────────────────

export {
    AUTH_BASE,
    authAxios,
};

export default axiosInstance;