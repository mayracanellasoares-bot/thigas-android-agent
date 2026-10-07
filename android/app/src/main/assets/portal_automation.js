(() => {
  const A = {};
  const norm = (s) => String(s ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/\s+/g, ' ').trim().toLowerCase();
  const visible = (el) => !!(el && (el.offsetWidth || el.offsetHeight || el.getClientRects().length));
  const text = (el) => norm(el?.innerText || el?.textContent || el?.value || el?.getAttribute?.('aria-label') || '');
  const all = (sel='button,a,input,select,textarea,[role="button"],label') => [...document.querySelectorAll(sel)].filter(visible);
  const dispatch = (el) => { ['input','change','blur'].forEach(n => el.dispatchEvent(new Event(n,{bubbles:true}))); };
  const safeClick = (el) => { if (!el || !visible(el)) return false; el.scrollIntoView({block:'center'}); el.click(); return true; };

  function exactText(candidates, selector='button,a,[role="button"]') {
    const c = candidates.map(norm);
    return all(selector).find(el => c.includes(text(el))) || null;
  }
  function containsText(candidates, selector='button,a,[role="button"]') {
    const c = candidates.map(norm);
    return all(selector).find(el => c.some(v => v && text(el).includes(v))) || null;
  }
  function closestSection(headingText) {
    const target = norm(headingText);
    const nodes = all('h1,h2,h3,h4,h5,h6,div,span,p,strong');
    const heading = nodes.find(el => text(el) === target || text(el).startsWith(target));
    if (!heading) return null;
    let node = heading;
    for (let i=0;i<5 && node;i++, node=node.parentElement) {
      if (node.querySelectorAll && node.querySelectorAll('button,a,[role="button"]').length) return node;
    }
    return heading.parentElement;
  }
  function clickInSection(sectionTitle, actionText) {
    const section = closestSection(sectionTitle);
    if (!section) return false;
    const c = norm(actionText);
    const el = [...section.querySelectorAll('button,a,[role="button"]')].filter(visible)
      .find(x => text(x) === c || text(x).includes(c));
    return safeClick(el);
  }
  function optionMatch(select, wanted) {
    const w = norm(wanted);
    if (!w) return null;
    const compact = (x) => norm(x).replace(/[^a-z0-9]/g,'');
    const aliases = {
      'tec':['tecnologia','tecnologia e inovacao','tec'],
      'lp':['lingua portuguesa','portugues','lp'],
      'sociologia':['sociologia']
    };
    const classMatch = w.match(/(\d)[^a-z0-9]*([a-z])$/i);
    const classAliases = classMatch ? [
      `${classMatch[1]} ${classMatch[2]}`,
      `${classMatch[1]}º ${classMatch[2]}`,
      `${classMatch[1]}º ano ${classMatch[2]}`,
      `${classMatch[1]} ano ${classMatch[2]}`
    ] : [];
    const ws = [w, ...(aliases[w] || []), ...classAliases];
    return [...select.options].find(o => ws.some(x => {
      const ot = norm(o.textContent);
      return ot.includes(norm(x)) || compact(ot).includes(compact(x));
    })) || null;
  }
  function selectByLabel(labelCandidates, wanted) {
    const labels = all('label,span,div,p').filter(el => labelCandidates.map(norm).some(v => text(el) === v || text(el).startsWith(v)));
    for (const label of labels) {
      const forId = label.getAttribute?.('for');
      let sel = forId ? document.getElementById(forId) : null;
      if (!sel || sel.tagName !== 'SELECT') sel = label.parentElement?.querySelector?.('select');
      if (!sel) sel = label.closest?.('div')?.querySelector?.('select');
      if (sel) {
        const o = optionMatch(sel, wanted);
        if (o) { sel.value = o.value; dispatch(sel); return {ok:true, value:o.textContent}; }
      }
    }
    return {ok:false};
  }
  function selectOnlyNonPlaceholder(labelCandidates) {
    const labels = all('label,span,div,p').filter(el => labelCandidates.map(norm).some(v => text(el) === v || text(el).startsWith(v)));
    for (const label of labels) {
      const sel = label.parentElement?.querySelector?.('select') || label.closest?.('div')?.querySelector?.('select');
      if (!sel) continue;
      const opts = [...sel.options].filter(o => o.value && !/selecione|select/i.test(o.textContent));
      if (opts.length === 1) { sel.value = opts[0].value; dispatch(sel); return {ok:true, value:opts[0].textContent}; }
    }
    return {ok:false};
  }
  function rowContainers() {
    const statusEls = all('button,[role="button"]')
      .filter(el => ['c','f'].includes(text(el)));
    const rows = [];
    for (const st of statusEls) {
      let n = st;
      for (let i=0;i<5 && n;i++, n=n.parentElement) {
        const ts = norm(n.innerText);
        const statusCount = n.querySelectorAll?.('button,[role="button"]')?.length || 0;
        if (ts.length > 3 && statusCount >= 1 && statusCount <= 8) { rows.push(n); break; }
      }
    }
    return [...new Set(rows)];
  }
  function studentRows() {
    return rowContainers().map(row => {
      const status = [...row.querySelectorAll('button,[role="button"]')].filter(visible).filter(b => ['c','f'].includes(text(b)));
      const raw = norm(row.innerText).replace(/\b[cf]\b/g,' ').replace(/\s+/g,' ').trim();
      return {row, raw, status};
    }).filter(x => x.status.length);
  }
  function resolveStudent(query, rows) {
    const q = norm(query);
    if (!q) return {kind:'none'};
    let matches = rows.filter(r => r.raw === q);
    if (!matches.length) matches = rows.filter(r => r.raw.includes(q) || q.includes(r.raw));
    if (matches.length === 1) return {kind:'one', value:matches[0]};
    if (matches.length > 1) return {kind:'ambiguous', matches:matches.map(x=>x.raw)};
    return {kind:'missing'};
  }
  function setStatusButton(btn, desired) {
    const cur = text(btn).toUpperCase();
    if (cur === desired) return true;
    safeClick(btn);
    return true;
  }
  function applyAttendance(absentees) {
    const rows = studentRows();
    if (!rows.length) return {ok:false, reason:'student_rows_not_found'};
    for (const r of rows) for (const b of r.status) setStatusButton(b, 'C');
    const missing = [], ambiguous = [];
    for (const name of absentees || []) {
      const found = resolveStudent(name, rows);
      if (found.kind === 'missing') missing.push(name);
      if (found.kind === 'ambiguous') ambiguous.push({name, matches:found.matches});
      if (found.kind === 'one') for (const b of found.value.status) setStatusButton(b, 'F');
    }
    if (missing.length || ambiguous.length) return {ok:false, reason:'student_resolution', missing, ambiguous};
    return {ok:true, students:rows.length, absentees:(absentees||[]).length};
  }
  function checkTimes(times) {
    const wanted = (times || []).map(norm);
    let count=0;
    const checks = [...document.querySelectorAll('input[type="checkbox"]')].filter(visible);
    for (const cb of checks) {
      let n=cb.parentElement;
      let context='';
      for(let i=0;i<3 && n;i++,n=n.parentElement) context += ' '+(n.innerText||'');
      const c=norm(context);
      if (wanted.some(w => c.includes(w))) {
        if (!cb.checked) cb.click();
        count++;
      }
    }
    return {ok: count >= wanted.length, selected:count, requested:wanted.length};
  }
  function fillLessonContent(content) {
    const fields = all('textarea,input[type="text"]')
      .filter(el => {
        const p = norm(el.placeholder || '');
        const ctx = norm(el.parentElement?.innerText || '');
        return el.tagName === 'TEXTAREA' || p.includes('resumo') || ctx.includes('resumo da aula');
      });
    if (!fields.length) return {ok:false, reason:'lesson_fields_not_found'};
    for (const f of fields) {
      f.focus(); f.value = content; dispatch(f);
    }
    return {ok:true, fields:fields.length};
  }
  function saveButton() {
    return exactText(['Salvar'], 'button,a,[role="button"]') || containsText(['Salvar'], 'button,a,[role="button"]');
  }

  A.scan = () => {
    const items = all('button,a,input,select,textarea,[role="button"],label').slice(0,500).map((el,i) => ({
      i, tag:el.tagName.toLowerCase(), type:el.getAttribute('type'), text:(el.innerText||el.textContent||'').trim().slice(0,160),
      value:(el.value||'').toString().slice(0,120), name:el.getAttribute('name'), id:el.id||null,
      aria:el.getAttribute('aria-label'), role:el.getAttribute('role'), placeholder:el.getAttribute('placeholder')
    }));
    return {url:location.href,title:document.title,body:(document.body?.innerText||'').slice(0,4000),items};
  };

  A.step = (plan, phase) => {
    const body = norm(document.body?.innerText || '');
    const out = {ok:true, phase, url:location.href, message:''};
    if (!(location.hostname === 'educacao.sp.gov.br' || location.hostname.endsWith('.educacao.sp.gov.br'))) return {...out, ok:false, needsUser:true, message:'Aguardando a Sala do Futuro.'};

    if (phase === 'open_frequency') {
      if (body.includes('lancamento da frequencia')) return {...out,nextPhase:'attendance_filters',message:'Tela de frequência aberta'};
      if (clickInSection('Frequência','Lançamento')) return {...out,nextPhase:'attendance_filters',message:'Abrindo Frequência > Lançamento'};
      return {...out,ok:false,needsUser:true,message:'Não encontrei Frequência > Lançamento. Use Mapear.'};
    }

    if (phase === 'attendance_filters') {
      if (body.includes('horario de aula') || body.includes('data da frequencia')) return {...out,nextPhase:'attendance_periods',message:'Filtros já aplicados'};
      const inferredTeaching = /^[6-9]/.test(plan.className || '') ? 'Ensino Fundamental Anos Finais' : (/^[123].*(serie|ª|a)/i.test(plan.className || '') ? 'Ensino Médio' : '');
      const teaching = inferredTeaching ? selectByLabel(['Tipo de Ensino','Tipo de ensino'], inferredTeaching) : selectOnlyNonPlaceholder(['Tipo de Ensino','Tipo de ensino']);
      const discipline = selectByLabel(['Disciplina'], plan.subject);
      const turma = selectByLabel(['Turma','Classe'], plan.className);
      if (!discipline.ok) return {...out,ok:false,needsUser:true,message:'Selecione a disciplina manualmente; depois toque em Continuar.',detail:{discipline,teaching,turma}};
      return {...out,nextPhase:'attendance_periods',message:'Filtros preenchidos',detail:{discipline,teaching,turma}};
    }

    if (phase === 'attendance_periods') {
      const t = checkTimes(plan.periodSlots || []);
      if (!t.ok && (plan.periodSlots||[]).length) return {...out,ok:false,needsUser:true,message:'Não consegui identificar todos os horários. Selecione-os manualmente e toque em Continuar.',detail:t};
      return {...out,nextPhase:'attendance_students',message:'Horários selecionados',detail:t};
    }

    if (phase === 'attendance_students') {
      const a = applyAttendance(plan.absentees || []);
      if (!a.ok) return {...out,ok:false,needsUser:true,message:'Preciso de correção na lista de alunos.',detail:a};
      return {...out,nextPhase:'attendance_save',message:'Frequência preparada',detail:a};
    }

    if (phase === 'attendance_save') {
      const b = saveButton();
      if (!b) return {...out,ok:false,needsUser:true,message:'Botão Salvar não encontrado. Use Mapear.'};
      safeClick(b);
      return {...out,nextPhase:'attendance_wait_saved',message:'Salvando frequência'};
    }

    if (phase === 'attendance_wait_saved') {
      if (body.includes('alteracoes salvas')) {
        const r = exactText(['Registro de aulas','Registro de aula'],'button,a,[role="button"]') || containsText(['Registro de aulas'],'button,a,[role="button"]');
        if (r) safeClick(r);
        return {...out,nextPhase:'lesson_form',message:'Frequência confirmada; abrindo Registro de aulas'};
      }
      return {...out,waiting:true,message:'Aguardando confirmação “Alterações salvas”'};
    }

    if (phase === 'lesson_form') {
      if (!body.includes('registro de aula')) return {...out,waiting:true,message:'Aguardando Registro de aulas'};
      const t = checkTimes(plan.periodSlots || []);
      const f = fillLessonContent(plan.content || '');
      if (!f.ok) return {...out,ok:false,needsUser:true,message:'Campos de conteúdo não identificados. Use Mapear.',detail:{t,f}};
      return {...out,nextPhase:'lesson_save',message:'Registro de aula preenchido',detail:{t,f}};
    }

    if (phase === 'lesson_save') {
      const b = saveButton();
      if (!b) return {...out,ok:false,needsUser:true,message:'Botão Salvar do Registro de aulas não encontrado.'};
      safeClick(b);
      return {...out,nextPhase:'lesson_wait_saved',message:'Salvando registro de aula'};
    }

    if (phase === 'lesson_wait_saved') {
      if (body.includes('registro salvo')) return {...out,nextPhase:'done',done:true,message:'Frequência e registro de aula confirmados'};
      return {...out,waiting:true,message:'Aguardando confirmação “Registro salvo”'};
    }

    if (phase === 'done') return {...out,done:true,message:'Concluído'};
    return {...out,ok:false,needsUser:true,message:'Fase desconhecida'};
  };

  window.ThigasPortal = A;
  return true;
})();
