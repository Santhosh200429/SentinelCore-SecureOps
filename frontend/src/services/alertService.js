import axiosInstance from '../api/axios.js';

const alertService = {
    getLive: () => axiosInstance.get('/api/alerts/live'),
};

export default alertService;
