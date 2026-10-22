(() => {
  'use strict';

  const pages = {
    admin: { name: '管理员工作台', description: '在这里查看当前账号与授权菜单。组织、课程和教学授权页面将随业务开发开放。' },
    teacher: { name: '教师工作台', description: '在这里查看当前账号与授权菜单。课程建设、备课和课堂教学页面将随业务开发开放。' },
    student: { name: '学生工作台', description: '在这里查看当前账号与授权菜单。课程学习、课堂活动和作业页面将随业务开发开放。' }
  };
  const tokenKey = 'zhijiao.login.token';
  const roleKey = 'zhijiao.login.role';
  const pageRole = document.body.dataset.role;
  const loading = document.getElementById('loading');
  const app = document.getElementById('roleApp');
  const retryButton = document.getElementById('retryBtn');

  function clearSession() {
    sessionStorage.removeItem(tokenKey);
    sessionStorage.removeItem(roleKey);
  }

  function goToLogin(reason = '') {
    window.location.replace(`/login.html${reason ? `?logout=${reason}` : ''}`);
  }

  async function api(path, options = {}) {
    const response = await fetch(path, { cache: 'no-store', ...options });
    let result;
    try {
      result = await response.json();
    } catch {
      throw new Error('服务器响应无法解析');
    }
    if (!response.ok || result.code !== 200) {
      const error = new Error(result.msg || `请求失败（${response.status}）`);
      error.unauthorized = response.status === 401 || result.code === 401;
      throw error;
    }
    return result;
  }

  function showLoading(message, retry = false) {
    app.hidden = true;
    loading.hidden = false;
    loading.querySelector('span').textContent = message;
    retryButton.hidden = !retry;
  }

  function render(info, routes) {
    const page = pages[pageRole];
    const user = info.user || {};
    const menus = Array.isArray(routes.data) ? routes.data : [];
    document.getElementById('roleTag').textContent = page.name;
    document.getElementById('sidebarRole').textContent = page.name;
    document.getElementById('roleName').textContent = page.name;
    document.getElementById('greeting').textContent = `欢迎，${user.nickName || user.userName || '已登录用户'}`;
    document.getElementById('roleDescription').textContent = page.description;
    document.getElementById('accountValue').textContent = user.userName || '—';
    document.getElementById('rolesValue').textContent = info.roles.join('、');
    document.getElementById('menuCount').textContent = `${menus.length} 个一级入口`;
    const list = document.getElementById('menuList');
    list.replaceChildren();
    if (menus.length === 0) {
      const item = document.createElement('li');
      item.className = 'empty';
      item.textContent = '当前账号暂无已开放菜单。';
      list.appendChild(item);
    } else {
      menus.forEach(menu => {
        const item = document.createElement('li');
        item.textContent = menu.meta?.title || menu.name || menu.path || '未命名菜单';
        list.appendChild(item);
      });
    }
    loading.hidden = true;
    app.hidden = false;
  }

  async function initialize() {
    showLoading('正在核验登录信息…');
    const token = sessionStorage.getItem(tokenKey);
    if (!token || !pages[pageRole]) {
      clearSession();
      goToLogin();
      return;
    }
    try {
      const headers = { Authorization: `Bearer ${token}` };
      const info = await api('/getInfo', { headers });
      const roles = Array.isArray(info.roles) ? info.roles : [];
      if (!roles.includes(pageRole)) {
        const allowedRole = [sessionStorage.getItem(roleKey), 'admin', 'teacher', 'student']
          .find(role => pages[role] && roles.includes(role));
        if (allowedRole) {
          sessionStorage.setItem(roleKey, allowedRole);
          window.location.replace(`/${allowedRole}.html`);
        } else {
          clearSession();
          goToLogin();
        }
        return;
      }
      const routes = await api('/getRouters', { headers });
      sessionStorage.setItem(roleKey, pageRole);
      render(info, routes);
    } catch (error) {
      if (error.unauthorized) {
        clearSession();
        goToLogin();
      } else {
        showLoading(`无法加载工作台：${error.message}`, true);
      }
    }
  }

  retryButton.addEventListener('click', initialize);
  document.getElementById('logoutBtn').addEventListener('click', async () => {
    const button = document.getElementById('logoutBtn');
    button.disabled = true;
    let reason = 'ok';
    try {
      await api('/logout', {
        method: 'POST',
        headers: { Authorization: `Bearer ${sessionStorage.getItem(tokenKey)}` }
      });
    } catch {
      reason = 'unconfirmed';
    }
    clearSession();
    goToLogin(reason);
  });
  window.addEventListener('pagehide', () => { app.hidden = true; });
  window.addEventListener('pageshow', event => { if (event.persisted) initialize(); });
  initialize();
})();
