import axiosInstance from '../api/axios.js';

const aiAssistantService = {
    chat: async (message, conversation, currentPage, currentRoute) => {
        return axiosInstance.post('/api/ai/chat', {
            message,
            conversation,
            currentPage,
            currentRoute
        });
    }
};

export default aiAssistantService;
