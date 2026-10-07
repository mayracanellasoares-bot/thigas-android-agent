(() => {
  const A = {};

  const norm = (s) => String(s ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();

  const visible = (el) => {
    if (!el) return false;
    const s = window.getComputedStyle
      ? getComputedStyle(el)
      : null;

    if (
      s &&
      (
        s.display === 'none' ||
        s.visibility === 'hidden' ||
        s.opacity === '0'
      )
    ) {
      return false;
    }

    return !el.disabled;
  };

  const text = (el) => norm(
    el?.innerText ||
    el?.textContent ||
    el?.value ||
    el?.getAttribute?.('aria-label') ||
    ''
  );

  const all = (
    sel =
      'button,a,input,select,textarea,[role="button"],label'
  ) =>
    [...document.querySelectorAll(sel)]
      .filter(visible);

  const dispatch = (el) => {
    [
      'input',
      'change',
      'blur'
    ].forEach(
      n =>
        el.dispatchEvent(
          new Event(
            n,
            {
              bubbles: true
            }
          )
        )
    );
  };

  const safeClick = (el) => {
    if (
      !el ||
      !visible(el)
    ) {
      return false;
    }

    try {
      el.scrollIntoView(
        {
          block: 'center'
        }
      );
    } catch (e) {}

    el.click();
    return true;
  };

  function clickByTextAnywhere(candidates) {
    const wanted = candidates.map(norm);
    const nodes = all(
      'a,button,[role="button"],[role="menuitem"],li,div,span'
    )
      .filter(el => {
        const t = text(el);
        return t && wanted.some(v => t === v || t.includes(v));
      })
      .sort((a,b) => text(a).length - text(b).length);

    for (const el of nodes) {
      if (safeClick(el)) return true;
    }
    return false;
  }

  function clickHrefLike(parts) {
    const wanted = parts.map(norm);
    const links = all('a[href]').filter(el => {
      const href = norm(el.getAttribute('href') || '');
      const t = text(el);
      return wanted.some(v => href.includes(v) || t.includes(v));
    });
    return links.length ? safeClick(links[0]) : false;
  }

  function openNavigationMenu() {
    const direct = all(
      'button,[role="button"],a'
    ).find(el => {
      const aria = norm(el.getAttribute?.('aria-label') || '');
      const title = norm(el.getAttribute?.('title') || '');
      const t = text(el);
      return (
        aria.includes('menu') ||
        aria.includes('naveg') ||
        aria.includes('expand') ||
        aria.includes('abrir') ||
        title.includes('menu') ||
        title.includes('naveg') ||
        t === 'menu'
      );
    });

    if (direct && safeClick(direct)) return true;

    const sideCandidates = all(
      'button,[role="button"]'
    ).filter(el => {
      const r = el.getBoundingClientRect?.();
      if (!r) return false;
      const t = text(el);
      return (
        r.left < 90 &&
        r.top > 120 &&
        r.top < window.innerHeight * 0.75 &&
        r.width < 100 &&
        r.height < 100 &&
        (
          t === '' ||
          t === '>' ||
          t === '<'
        )
      );
    });

    return sideCandidates.length
      ? safeClick(sideCandidates[0])
      : false;
  }

  function navigateToAttendance() {
    const body = norm(document.body?.innerText || '');

    if (attendanceDetailsVisible()) {
      return {
        ok: true,
        nextPhase: 'attendance_periods',
        message: 'Lançamento da frequência já aberto'
      };
    }

    if (
      body.includes('lancamento da frequencia') &&
      !body.includes('lancamento da frequencia detalhes')
    ) {
      const details = clickByTextAnywhere([
        'detalhes',
        'lançar frequência',
        'lancar frequencia',
        'lançamento',
        'lancamento'
      ]);

      if (details) {
        return {
          ok: true,
          waiting: true,
          nextPhase: 'open_frequency',
          message: 'Abrindo detalhes da frequência'
        };
      }
    }

    if (
      clickHrefLike([
        'diario-de-classe',
        'diario',
        'frequencia',
        'frequência',
        'lancamento',
        'lançamento'
      ])
    ) {
      return {
        ok: true,
        waiting: true,
        nextPhase: 'open_frequency',
        message: 'Navegando pelo Diário de Classe'
      };
    }

    if (
      clickByTextAnywhere([
        'diário de classe',
        'diario de classe'
      ])
    ) {
      return {
        ok: true,
        waiting: true,
        nextPhase: 'open_frequency',
        message: 'Abrindo Diário de Classe'
      };
    }

    if (
      clickByTextAnywhere([
        'frequência',
        'frequencia'
      ])
    ) {
      return {
        ok: true,
        waiting: true,
        nextPhase: 'open_frequency',
        message: 'Abrindo Frequência'
      };
    }

    if (
      clickByTextAnywhere([
        'lançamento',
        'lancamento'
      ])
    ) {
      return {
        ok: true,
        waiting: true,
        nextPhase: 'open_frequency',
        message: 'Abrindo Lançamento'
      };
    }

    if (openNavigationMenu()) {
      return {
        ok: true,
        waiting: true,
        nextPhase: 'open_frequency',
        message: 'Abrindo menu do portal'
      };
    }

    return {
      ok: false,
      retry: true,
      waiting: true,
      nextPhase: 'open_frequency',
      message: 'Procurando Diário de Classe no portal'
    };
  }

  function exactText(
    candidates,
    selector =
      'button,a,[role="button"]'
  ) {
    const c =
      candidates.map(norm);

    return (
      all(selector)
        .find(
          el =>
            c.includes(
              text(el)
            )
        ) ||
      null
    );
  }

  function containsText(
    candidates,
    selector =
      'button,a,[role="button"]'
  ) {
    const c =
      candidates.map(norm);

    return (
      all(selector)
        .find(
          el =>
            c.some(
              v =>
                v &&
                text(el).includes(v)
            )
        ) ||
      null
    );
  }

  function closestSection(
    headingText
  ) {
    const target =
      norm(
        headingText
      );

    const nodes =
      all(
        'h1,h2,h3,h4,h5,h6,div,span,p,strong'
      );

    const heading =
      nodes.find(
        el =>
          text(el) === target ||
          text(el).startsWith(
            target
          )
      );

    if (!heading) {
      return null;
    }

    let node =
      heading;

    for (
      let i = 0;
      i < 6 && node;
      i++,
      node = node.parentElement
    ) {
      if (
        node.querySelectorAll &&
        node.querySelectorAll(
          'button,a,[role="button"],input,select'
        ).length
      ) {
        return node;
      }
    }

    return heading.parentElement;
  }

  function clickInSection(
    sectionTitle,
    actionText
  ) {
    const section =
      closestSection(
        sectionTitle
      );

    if (!section) {
      return false;
    }

    const c =
      norm(
        actionText
      );

    const el =
      [
        ...section
          .querySelectorAll(
            'button,a,[role="button"]'
          )
      ]
        .filter(visible)
        .find(
          x =>
            text(x) === c ||
            text(x).includes(c)
        );

    return safeClick(el);
  }

  function optionMatch(
    select,
    wanted
  ) {
    const w =
      norm(wanted);

    if (!w) {
      return null;
    }

    const compact =
      (x) =>
        norm(x)
          .replace(
            /[^a-z0-9]/g,
            ''
          );

    const aliases =
      {
        'tec': [
          'tecnologia',
          'tecnologia e inovacao',
          'tec'
        ],
        'lp': [
          'lingua portuguesa',
          'portugues',
          'lp'
        ],
        'sociologia': [
          'sociologia'
        ]
      };

    const classMatch =
      w.match(
        /(\d)[^a-z0-9]*([a-z])$/i
      );

    const classAliases =
      classMatch
        ? [
            classMatch[1] +
              ' ' +
              classMatch[2],
            classMatch[1] +
              'º ' +
              classMatch[2],
            classMatch[1] +
              'º ano ' +
              classMatch[2],
            classMatch[1] +
              ' ano ' +
              classMatch[2]
          ]
        : [];

    const ws =
      [w]
        .concat(
          aliases[w] ||
            []
        )
        .concat(
          classAliases
        );

    return (
      [...select.options]
        .find(
          o =>
            ws.some(
              x => {
                const ot =
                  norm(
                    o.textContent
                  );

                return (
                  ot.includes(
                    norm(x)
                  ) ||
                  compact(ot)
                    .includes(
                      compact(x)
                    )
                );
              }
            )
        ) ||
      null
    );
  }

  function selectByLabel(
    labelCandidates,
    wanted
  ) {
    const labels =
      all(
        'label,span,div,p'
      )
        .filter(
          el =>
            labelCandidates
              .map(norm)
              .some(
                v =>
                  text(el) === v ||
                  text(el)
                    .startsWith(v)
              )
        );

    for (
      const label
      of labels
    ) {
      const forId =
        label.getAttribute?.(
          'for'
        );

      let sel =
        forId
          ? document
              .getElementById(
                forId
              )
          : null;

      if (
        !sel ||
        sel.tagName !== 'SELECT'
      ) {
        sel =
          label.parentElement
            ?.querySelector?.(
              'select'
            );
      }

      if (!sel) {
        sel =
          label.closest?.(
            'div'
          )?.querySelector?.(
            'select'
          );
      }

      if (sel) {
        const o =
          optionMatch(
            sel,
            wanted
          );

        if (o) {
          sel.value =
            o.value;

          dispatch(sel);

          return {
            ok: true,
            value:
              o.textContent
          };
        }
      }
    }

    return {
      ok: false
    };
  }

  function selectOnlyNonPlaceholder(
    labelCandidates
  ) {
    const labels =
      all(
        'label,span,div,p'
      )
        .filter(
          el =>
            labelCandidates
              .map(norm)
              .some(
                v =>
                  text(el) === v ||
                  text(el)
                    .startsWith(v)
              )
        );

    for (
      const label
      of labels
    ) {
      const sel =
        label.parentElement
          ?.querySelector?.(
            'select'
          ) ||
        label.closest?.(
          'div'
        )?.querySelector?.(
          'select'
        );

      if (!sel) {
        continue;
      }

      const opts =
        [...sel.options]
          .filter(
            o =>
              o.value &&
              !/selecione|select/i
                .test(
                  o.textContent
                )
          );

      if (
        opts.length === 1
      ) {
        sel.value =
          opts[0].value;

        dispatch(sel);

        return {
          ok: true,
          value:
            opts[0]
              .textContent
        };
      }
    }

    return {
      ok: false
    };
  }

  function rowContainers() {
    const statusEls =
      all(
        'button,[role="button"]'
      )
        .filter(
          el =>
            [
              'c',
              'f'
            ].includes(
              text(el)
            )
        );

    const rows =
      [];

    for (
      const st
      of statusEls
    ) {
      let n =
        st;

      for (
        let i = 0;
        i < 5 && n;
        i++,
        n = n.parentElement
      ) {
        const ts =
          norm(
            n.innerText
          );

        const statusCount =
          n.querySelectorAll?.(
            'button,[role="button"]'
          )?.length ||
          0;

        if (
          ts.length > 3 &&
          statusCount >= 1 &&
          statusCount <= 8
        ) {
          rows.push(n);
          break;
        }
      }
    }

    return [
      ...new Set(rows)
    ];
  }

  function studentRows() {
    return rowContainers()
      .map(
        row => {
          const status =
            [
              ...row.querySelectorAll(
                'button,[role="button"]'
              )
            ]
              .filter(
                visible
              )
              .filter(
                b =>
                  [
                    'c',
                    'f'
                  ].includes(
                    text(b)
                  )
              );

          const raw =
            norm(
              row.innerText
            )
              .replace(
                /\b[cf]\b/g,
                ' '
              )
              .replace(
                /\s+/g,
                ' '
              )
              .trim();

          return {
            row,
            raw,
            status
          };
        }
      )
      .filter(
        x =>
          x.status.length
      );
  }

  function resolveStudent(
    query,
    rows
  ) {
    const q =
      norm(
        query
      );

    if (!q) {
      return {
        kind:
          'none'
      };
    }

    let matches =
      rows.filter(
        r =>
          r.raw === q
      );

    if (!matches.length) {
      matches =
        rows.filter(
          r =>
            r.raw.includes(q) ||
            q.includes(
              r.raw
            )
        );
    }

    if (
      matches.length === 1
    ) {
      return {
        kind:
          'one',
        value:
          matches[0]
      };
    }

    if (
      matches.length > 1
    ) {
      return {
        kind:
          'ambiguous',
        matches:
          matches.map(
            x =>
              x.raw
          )
      };
    }

    return {
      kind:
        'missing'
    };
  }

  function setStatusButton(
    btn,
    desired
  ) {
    const cur =
      text(btn)
        .toUpperCase();

    if (
      cur === desired
    ) {
      return true;
    }

    safeClick(btn);

    return true;
  }

  function applyAttendance(
    absentees
  ) {
    const rows =
      studentRows();

    if (
      !rows.length
    ) {
      return {
        ok: false,
        reason:
          'student_rows_not_found'
      };
    }

    for (
      const r
      of rows
    ) {
      for (
        const b
        of r.status
      ) {
        setStatusButton(
          b,
          'C'
        );
      }
    }

    const missing =
      [];

    const ambiguous =
      [];

    for (
      const name
      of absentees ||
        []
    ) {
      const found =
        resolveStudent(
          name,
          rows
        );

      if (
        found.kind ===
        'missing'
      ) {
        missing.push(
          name
        );
      }

      if (
        found.kind ===
        'ambiguous'
      ) {
        ambiguous.push(
          {
            name,
            matches:
              found.matches
          }
        );
      }

      if (
        found.kind ===
        'one'
      ) {
        for (
          const b
          of found.value.status
        ) {
          setStatusButton(
            b,
            'F'
          );
        }
      }
    }

    if (
      missing.length ||
      ambiguous.length
    ) {
      return {
        ok: false,
        reason:
          'student_resolution',
        missing,
        ambiguous
      };
    }

    return {
      ok: true,
      students:
        rows.length,
      absentees:
        (
          absentees ||
          []
        ).length
    };
  }

  function parseRange(
    raw
  ) {
    const m =
      String(
        raw ||
          ''
      ).match(
        /(\d{1,2}:\d{2}).*?(\d{1,2}:\d{2})/
      );

    return m
      ? [
          norm(
            m[1]
          ),
          norm(
            m[2]
          )
        ]
      : [];
  }


  function attendanceDetailsVisible() {
    const body = norm(document.body?.innerText || '');
    return (
      body.includes('lancamento da frequencia detalhes') ||
      (
        body.includes('horario de aula') &&
        body.includes('marcar todos como')
      ) ||
      (
        body.includes('horario de aula') &&
        body.includes('mostrar ativos e inativos')
      )
    );
  }

  function fieldNearLabel(labelCandidates) {
    const wanted = labelCandidates.map(norm);
    const labels = all('label,span,div,p,strong').filter(el => {
      const t = text(el);
      return wanted.some(v => t === v || t.startsWith(v));
    });

    for (const label of labels) {
      let node = label;
      for (let i = 0; i < 6 && node; i++, node = node.parentElement) {
        const controls = [
          ...node.querySelectorAll?.(
            'select,[role="combobox"],input,button,[role="button"],.p-dropdown,.p-multiselect,.mat-mdc-select,.mat-select'
          ) || []
        ].filter(visible);

        const candidate = controls.find(el => {
          const t = text(el);
          const aria = norm(el.getAttribute?.('aria-label') || '');
          const ph = norm(el.getAttribute?.('placeholder') || '');
          return (
            t.includes('selecione') ||
            aria.includes('selecione') ||
            ph.includes('selecione') ||
            el.tagName === 'SELECT' ||
            el.getAttribute?.('role') === 'combobox' ||
            String(el.className || '').toLowerCase().includes('dropdown') ||
            String(el.className || '').toLowerCase().includes('select')
          );
        });

        if (candidate) return candidate;
      }
    }
    return null;
  }

  function openTimePicker() {
    const field = fieldNearLabel(['Horário de Aula', 'Horario de Aula']);
    if (!field) return {ok:false, reason:'time_picker_not_found'};

    if (field.tagName === 'SELECT') {
      return {ok:true, native:true};
    }

    const expanded = norm(field.getAttribute?.('aria-expanded') || '');
    if (expanded !== 'true') safeClick(field);
    return {ok:true, native:false};
  }

  function optionNodes() {
    return all(
      '[role="option"],li,mat-option,.mat-mdc-option,.p-dropdown-item,.p-multiselect-item,[data-pc-section="item"],div'
    ).filter(el => {
      const t = text(el);
      return t && t.length < 180;
    });
  }

  function clickOptionForRange(raw) {
    const parts = parseRange(raw);
    if (parts.length < 2) return false;
    const [start,end] = parts;

    const candidates = optionNodes()
      .filter(el => {
        const t = text(el);
        return t.includes(start) && t.includes(end);
      })
      .sort((a,b) => text(a).length - text(b).length);

    if (!candidates.length) return false;
    return safeClick(candidates[0]);
  }

  function chooseTimeRanges(times) {
    const expanded = [];
    for (const raw of times || []) {
      const ranges = String(raw).match(
        /\b\d{1,2}:\d{2}\s*[-–—]\s*\d{1,2}:\d{2}\b/g
      );
      if (ranges && ranges.length) expanded.push(...ranges);
      else if (String(raw).trim()) expanded.push(String(raw));
    }

    const wanted = [...new Set(expanded)];
    if (!wanted.length) return {ok:true, selected:0, requested:0, wanted};

    const field = fieldNearLabel(['Horário de Aula', 'Horario de Aula']);
    if (field?.tagName === 'SELECT') {
      let selected = 0;
      for (const w of wanted) {
        const parts = parseRange(w);
        const opt = [...field.options].find(o => {
          const t = norm(o.textContent || '');
          return parts.length === 2 && t.includes(parts[0]) && t.includes(parts[1]);
        });
        if (opt) {
          opt.selected = true;
          selected++;
        }
      }
      dispatch(field);
      return {ok:selected >= wanted.length, selected, requested:wanted.length, wanted, native:true};
    }

    let selected = 0;
    let opened = openTimePicker().ok;

    for (let i = 0; i < wanted.length; i++) {
      if (!opened) break;

      if (clickOptionForRange(wanted[i])) {
        selected++;
      }

      if (i < wanted.length - 1) {
        const stillVisible = optionNodes().some(el => {
          const t = text(el);
          const next = parseRange(wanted[i + 1]);
          return next.length === 2 && t.includes(next[0]) && t.includes(next[1]);
        });
        if (!stillVisible) {
          opened = openTimePicker().ok;
        }
      }
    }

    return {ok:selected >= wanted.length, selected, requested:wanted.length, wanted, native:false};
  }

  function clickTimeRange(
    raw
  ) {
    const parts =
      parseRange(
        raw
      );

    if (
      parts.length <
      2
    ) {
      return false;
    }

    const start =
      parts[0];

    const end =
      parts[1];

    const nodes =
      all(
        'label,button,[role="checkbox"],[role="radio"],[role="button"],li,div,span'
      );

    const candidates =
      nodes
        .filter(
          el => {
            const t =
              text(el);

            return (
              t.includes(
                start
              ) &&
              t.includes(
                end
              )
            );
          }
        )
        .sort(
          (
            a,
            b
          ) =>
            text(a).length -
            text(b).length
        );

    for (
      const candidate
      of candidates
    ) {
      let n =
        candidate;

      for (
        let i = 0;
        i < 5 && n;
        i++,
        n = n.parentElement
      ) {
        const input =
          n.matches?.(
            'input[type="checkbox"],input[type="radio"]'
          )
            ? n
            : n.querySelector?.(
                'input[type="checkbox"],input[type="radio"]'
              );

        if (
          input &&
          visible(input)
        ) {
          if (
            !input.checked
          ) {
            input.click();
          }

          return true;
        }

        const role =
          n.getAttribute?.(
            'role'
          );

        if (
          role ===
            'checkbox' ||
          role ===
            'radio' ||
          role ===
            'button' ||
          n.tagName ===
            'BUTTON' ||
          n.tagName ===
            'LABEL'
        ) {
          safeClick(n);

          return true;
        }
      }
    }

    return false;
  }

  function checkTimes(
    times
  ) {
    const expanded =
      [];

    for (
      const raw
      of times ||
        []
    ) {
      const ranges =
        String(raw)
          .match(
            /\b\d{1,2}:\d{2}\s*[-–—]\s*\d{1,2}:\d{2}\b/g
          );

      if (
        ranges &&
        ranges.length
      ) {
        expanded.push(
          ...ranges
        );
      } else if (
        String(raw)
          .trim()
      ) {
        expanded.push(
          String(raw)
        );
      }
    }

    const wanted =
      [
        ...new Set(
          expanded
        )
      ];

    let selected =
      0;

    for (
      const w
      of wanted
    ) {
      if (
        clickTimeRange(
          w
        )
      ) {
        selected++;
      }
    }

    if (
      wanted.length ===
        1 &&
      selected === 0
    ) {
      const section =
        closestSection(
          'Horário de aula'
        ) ||
        closestSection(
          'Horario de aula'
        );

      const inputs =
        section
          ? [
              ...section.querySelectorAll(
                'input[type="checkbox"],input[type="radio"]'
              )
            ].filter(
              visible
            )
          : [];

      if (
        inputs.length ===
        1
      ) {
        if (
          !inputs[0]
            .checked
        ) {
          inputs[0]
            .click();
        }

        selected =
          1;
      }
    }

    return {
      ok:
        selected >=
        wanted.length,
      selected,
      requested:
        wanted.length,
      wanted
    };
  }

  function fillLessonContent(
    content
  ) {
    const fields =
      all(
        'textarea,input[type="text"]'
      )
        .filter(
          el => {
            const p =
              norm(
                el.placeholder ||
                  ''
              );

            const ctx =
              norm(
                el.parentElement
                  ?.innerText ||
                  ''
              );

            return (
              el.tagName ===
                'TEXTAREA' ||
              p.includes(
                'resumo'
              ) ||
              ctx.includes(
                'resumo da aula'
              )
            );
          }
        );

    if (
      !fields.length
    ) {
      return {
        ok: false,
        reason:
          'lesson_fields_not_found'
      };
    }

    for (
      const f
      of fields
    ) {
      f.focus();
      f.value =
        content;
      dispatch(f);
    }

    return {
      ok: true,
      fields:
        fields.length
    };
  }

  function saveButton() {
    return (
      exactText(
        [
          'Salvar'
        ],
        'button,a,[role="button"]'
      ) ||
      containsText(
        [
          'Salvar'
        ],
        'button,a,[role="button"]'
      )
    );
  }

  A.scan = () => {
    const items =
      all(
        'button,a,input,select,textarea,[role="button"],label'
      )
        .slice(
          0,
          500
        )
        .map(
          (
            el,
            i
          ) => ({
            i,
            tag:
              el.tagName
                .toLowerCase(),
            type:
              el.getAttribute(
                'type'
              ),
            text:
              (
                el.innerText ||
                el.textContent ||
                ''
              )
                .trim()
                .slice(
                  0,
                  160
                ),
            value:
              (
                el.value ||
                ''
              )
                .toString()
                .slice(
                  0,
                  120
                ),
            name:
              el.getAttribute(
                'name'
              ),
            id:
              el.id ||
              null,
            aria:
              el.getAttribute(
                'aria-label'
              ),
            role:
              el.getAttribute(
                'role'
              ),
            placeholder:
              el.getAttribute(
                'placeholder'
              )
          })
        );

    return {
      url:
        location.href,
      title:
        document.title,
      body:
        (
          document.body
            ?.innerText ||
          ''
        ).slice(
          0,
          4000
        ),
      items
    };
  };

  A.step = (
    plan,
    phase
  ) => {
    const body =
      norm(
        document.body
          ?.innerText ||
        ''
      );

    const out =
      {
        ok: true,
        phase,
        url:
          location.href,
        message: ''
      };

    if (
      !(
        location.hostname ===
          'educacao.sp.gov.br' ||
        location.hostname
          .endsWith(
            '.educacao.sp.gov.br'
          )
      )
    ) {
      return {
        ...out,
        ok: false,
        needsUser:
          true,
        message:
          'Aguardando a Sala do Futuro.'
      };
    }

    if (
      phase ===
      'open_frequency'
    ) {
      const nav =
        navigateToAttendance();

      return {
        ...out,
        ...nav
      };
    }

    if (
      phase ===
      'attendance_filters'
    ) {
      if (attendanceDetailsVisible()) {
        return {
          ...out,
          nextPhase:
            'attendance_periods',
          message:
            'Turma e disciplina já definidas no lançamento'
        };
      }

      if (
        body.includes(
          'horario de aula'
        ) ||
        body.includes(
          'data da frequencia'
        )
      ) {
        return {
          ...out,
          nextPhase:
            'attendance_periods',
          message:
            'Filtros já aplicados'
        };
      }

      const inferredTeaching =
        /^[6-9]/.test(
          plan.className ||
            ''
        )
          ? 'Ensino Fundamental Anos Finais'
          : (
              /^[123].*(serie|ª|a)/i
                .test(
                  plan.className ||
                    ''
                )
                ? 'Ensino Médio'
                : ''
            );

      const teaching =
        inferredTeaching
          ? selectByLabel(
              [
                'Tipo de Ensino',
                'Tipo de ensino'
              ],
              inferredTeaching
            )
          : selectOnlyNonPlaceholder(
              [
                'Tipo de Ensino',
                'Tipo de ensino'
              ]
            );

      const discipline =
        selectByLabel(
          [
            'Disciplina'
          ],
          plan.subject
        );

      const turma =
        selectByLabel(
          [
            'Turma',
            'Classe'
          ],
          plan.className
        );

      if (
        !discipline.ok
      ) {
        return {
          ...out,
          ok: false,
          needsUser:
            true,
          message:
            'Não consegui selecionar a disciplina automaticamente.',
          detail:
            {
              discipline,
              teaching,
              turma
            }
        };
      }

      return {
        ...out,
        nextPhase:
          'attendance_periods',
        message:
          'Filtros preenchidos',
        detail:
          {
            discipline,
            teaching,
            turma
          }
      };
    }

    if (
      phase ===
      'attendance_periods'
    ) {
      const t =
        checkTimes(
          plan.periodSlots ||
            []
        );

      if (
        !t.ok &&
        (
          plan.periodSlots ||
          []
        ).length
      ) {
        return {
          ...out,
          ok: false,
          needsUser:
            true,
          message:
            'Não consegui identificar todos os horários.',
          detail:
            t
        };
      }

      return {
        ...out,
        nextPhase:
          'attendance_students',
        message:
          'Horários selecionados',
        detail:
          t
      };
    }

    if (
      phase ===
      'attendance_students'
    ) {
      const a =
        applyAttendance(
          plan.absentees ||
            []
        );

      if (
        !a.ok
      ) {
        return {
          ...out,
          ok: false,
          needsUser:
            true,
          message:
            'Preciso de correção na lista de alunos.',
          detail:
            a
        };
      }

      return {
        ...out,
        nextPhase:
          'attendance_save',
        message:
          'Frequência preparada',
        detail:
          a
      };
    }

    if (
      phase ===
      'attendance_save'
    ) {
      const b =
        saveButton();

      if (!b) {
        return {
          ...out,
          ok: false,
          needsUser:
            true,
          message:
            'Botão Salvar não encontrado.'
        };
      }

      safeClick(b);

      return {
        ...out,
        nextPhase:
          'attendance_wait_saved',
        message:
          'Salvando frequência'
      };
    }

    if (
      phase ===
      'attendance_wait_saved'
    ) {
      if (
        body.includes(
          'alteracoes salvas'
        )
      ) {
        const r =
          exactText(
            [
              'Registro de aulas',
              'Registro de aula'
            ],
            'button,a,[role="button"]'
          ) ||
          containsText(
            [
              'Registro de aulas'
            ],
            'button,a,[role="button"]'
          );

        if (r) {
          safeClick(r);
        }

        return {
          ...out,
          nextPhase:
            'lesson_form',
          message:
            'Frequência confirmada; abrindo Registro de aulas'
        };
      }

      return {
        ...out,
        waiting:
          true,
        message:
          'Aguardando confirmação de frequência salva'
      };
    }

    if (
      phase ===
      'lesson_form'
    ) {
      if (
        !body.includes(
          'registro de aula'
        )
      ) {
        return {
          ...out,
          waiting:
            true,
          message:
            'Aguardando Registro de aulas'
        };
      }

      const t =
        checkTimes(
          plan.periodSlots ||
            []
        );

      const f =
        fillLessonContent(
          plan.content ||
            ''
        );

      if (
        !f.ok
      ) {
        return {
          ...out,
          ok: false,
          needsUser:
            true,
          message:
            'Campos de conteúdo não identificados.',
          detail:
            {
              t,
              f
            }
        };
      }

      return {
        ...out,
        nextPhase:
          'lesson_save',
        message:
          'Registro de aula preenchido',
        detail:
          {
            t,
            f
          }
      };
    }

    if (
      phase ===
      'lesson_save'
    ) {
      const b =
        saveButton();

      if (!b) {
        return {
          ...out,
          ok: false,
          needsUser:
            true,
          message:
            'Botão Salvar do Registro de aulas não encontrado.'
        };
      }

      safeClick(b);

      return {
        ...out,
        nextPhase:
          'lesson_wait_saved',
        message:
          'Salvando registro de aula'
      };
    }

    if (
      phase ===
      'lesson_wait_saved'
    ) {
      if (
        body.includes(
          'registro salvo'
        )
      ) {
        return {
          ...out,
          nextPhase:
            'done',
          done:
            true,
          message:
            'Frequência e registro de aula confirmados'
        };
      }

      return {
        ...out,
        waiting:
          true,
        message:
          'Aguardando confirmação de registro salvo'
      };
    }

    if (
      phase ===
      'done'
    ) {
      return {
        ...out,
        done:
          true,
        message:
          'Concluído'
      };
    }

    return {
      ...out,
      ok: false,
      needsUser:
        true,
      message:
        'Fase desconhecida'
    };
  };

  window.ThigasPortal =
    A;

  return true;
})();
