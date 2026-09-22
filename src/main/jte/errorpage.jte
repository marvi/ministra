@param String code
@param String title
@param String lead
@param String detail

@template.layout(title = title, content = @`
    <section class="status-page" aria-labelledby="status-title">
        <p class="status-code" aria-hidden="true">${code}</p>
        <h1 id="status-title">${title}</h1>
        <p class="lede">${lead}</p>
        <p>${detail}</p>
        <div class="actions">
            <a class="button" href="/">Till startsidan</a>
        </div>
    </section>
`)
