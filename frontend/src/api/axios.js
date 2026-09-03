/**
 * SentinelCore SecureOps — Central Axios Instance
 *
 * Purpose:
 *   Creates the shared HTTP clients used by all service modules.
 *
 * NORMAL API CLIENT:
 *   Uses VITE_API_URL.
 *   If VITE_API_URL ends with /api, normal requests use:
 *
 *       https://sentinelcore-secureops-o5wr.onrender.com/api/...
 *
 * AUTH CLIENT:
 *   Spring Security login/logout endpoints are NOT under /api.
 *   Therefore authentication requests use the backend root:
 *
 *       https://sentinelcore-secureops-o5wr.onrender.com/login
 *       https://sentinelcore-secureops-o5wr.onrender.com/logout
 *
 * This allows VITE_API_URL to remain:
 *
 *   https://sentinelcore-secureops-o5wr.onrender.com/api
 *
 * without breaking Spring Security authentication.
 *
 * Features:
 *   - withCredentials keeps JSESSIONID alive
 *   - CSRF interceptor reads XSRF-TOKEN
 *   - Normal API 401/403 handling is preserved
 *   - Authentication requests use a separate client so a failed
 *     login does not trigger the session-expired redirect.
 */

import axios from 'axios';

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reads the XSRF-TOKEN cookie set by Spring Security's CsrfFilter.
 *
 * @returns {string|null}
 */
function getCsrfToken() {
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);

    return match
        ? decodeURIComponent(match[1])
        : null;
}


// ─────────────────────────────────────────────────────────────────────────────
// API BASE URL
// ─────────────────────────────────────────────────────────────────────────────

// VITE_API_URL can remain:
// https://sentinelcore-secureops-o5wr.onrender.com/api
//
// Locally it can be empty so Vite's dev proxy handles /api/*.

const API_BASE = import.meta.env.VITE_API_URL || '';


// Remove trailing /api for Spring Security authentication.
//
// Example:
//
// API_BASE:
// https://sentinelcore-secureops-o5wr.onrender.com/api
//
// AUTH_BASE:
// https://sentinelcore-secureops-o5wr.onrender.com

const AUTH_BASE = API_BASE.endsWith('/api')
    ? API_BASE.slice(0, -4)
    : API_BASE;


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

const axiosInstance = axios.create({

    // Normal REST API requests use /api
    baseURL: API_BASE,

    // Send JSESSIONID session cookie
    withCredentials: true,

    headers: {
        'Content-Type': 'application/json',
    },
});


// ─────────────────────────────────────────────────────────────────────────────
// AUTHENTICATION CLIENT
// ─────────────────────────────────────────────────────────────────────────────
//
// IMPORTANT:
//
// Spring Security is configured with:
//
//     loginProcessingUrl("/login")
//
// and:
//
//     logoutRequestMatcher("/logout")
//
// Therefore these requests must NOT contain /api.
//
// This client uses AUTH_BASE instead of API_BASE.
//

const authAxios = axios.create({

    // Root backend URL, without /api
    baseURL: AUTH_BASE,

    // Required for JSESSIONID
    withCredentials: true,

    headers: {
        'Content-Type': 'application/json',
    },
});


// ─────────────────────────────────────────────────────────────────────────────
// CSRF INTERCEPTOR — NORMAL API CLIENT
// ─────────────────────────────────────────────────────────────────────────────

axiosInstance.interceptors.request.use(

    (config) => {

        const mutatingMethods = [
            'post',
            'put',
            'delete',
            'patch',
        ];

        const method = config.method?.toLowerCase();

        if (mutatingMethods.includes(method)) {

            const token = getCsrfToken();

            if (token) {

                config.headers['X-XSRF-TOKEN'] = token;
            }
        }

        return config;
    },

    (error) => {
        return Promise.reject(error);
    }
);


// ─────────────────────────────────────────────────────────────────────────────
// CSRF INTERCEPTOR — AUTH CLIENT
// ─────────────────────────────────────────────────────────────────────────────
//
// Login/logout currently have CSRF disabled in SecurityConfig:
//
//     .ignoringRequestMatchers(
//         "/api/**",
//         "/login",
//         "/logout",
//         "/register"
//     )
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

        const method = config.method?.toLowerCase();

        if (mutatingMethods.includes(method)) {

            const token = getCsrfToken();

            if (token) {

                config.headers['X-XSRF-TOKEN'] = token;
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
// Handles session expiration for normal authenticated API requests.
//
// IMPORTANT:
// The authAxios client intentionally does NOT use this interceptor.
// Therefore a failed /login request will remain a normal 401 response and
// will not redirect the browser.
//

axiosInstance.interceptors.response.use(

    (response) => {
        return response;
    },

    (error) => {

        if (error.response?.status === 401) {

            const currentPath =
                window.location.pathname;

            const requestUrl =
                error.config?.url || '';

            // Already on login page
            const isLoginPage =
                currentPath === '/login' ||
                currentPath.startsWith('/login');

            // Initial session check
            const isSessionCheck =
                requestUrl.includes('/api/dashboard/user');

            if (!isLoginPage && !isSessionCheck) {

                window.location.href =
                    '/login?expired';
            }

        } else if (error.response?.status === 403) {

            window.location.href = '/403';
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