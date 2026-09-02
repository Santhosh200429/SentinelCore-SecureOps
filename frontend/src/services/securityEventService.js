import axiosInstance from '../api/axios.js';
const securityEventService = { getAll: () => axiosInstance.get('/api/security-events') };
export default securityEventService;
