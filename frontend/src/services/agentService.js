import axiosInstance from '../api/axios.js';

const agentService = {
  list: () => axiosInstance.get('/api/agents'),
  enroll: (name) => axiosInstance.post('/api/agents/enroll', { name }),
  revoke: (id) => axiosInstance.delete(`/api/agents/${id}`),
  latest: (assetId) => axiosInstance.get(`/api/agents/telemetry/${assetId}`),
};
export default agentService;
