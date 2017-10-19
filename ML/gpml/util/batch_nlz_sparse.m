function nlZ  = batch_nlz_sparse(mean, lik, x_batch,  y_batch,  hyp, R0, Ku_batch, m_0, diagK_batch, R, C_u, m_u)
	nu=size(m_u,1);
	const_term=-0.5*nu+sum(log(diag(R)));%ok
	lik_name = func2str(lik{1});

	alpha=R0'*R0*(m_u-m_0);
	tmp1=C_u'*R0';
	tmp2=R*alpha;%ok
	m_batch = feval(mean{:}, hyp.mean, x_batch);  
	V_batch = R0*Ku_batch;
	H_batch=R0'*V_batch;
	post_m_batch=m_batch+H_batch'*(m_u-m_0);
	V1_batch=tmp1*V_batch;
	d0_batch = diagK_batch-sum(V_batch.*V_batch,1)';   
	post_v_batch=d0_batch+sum(V1_batch.*V1_batch,1)';

	switch lik_name
	case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
		ll_batch = E_log_p(lik_name, y_batch, post_m_batch, post_v_batch, hyp.lik);
	otherwise
		ll_batch = likKL(post_v_batch, lik, hyp.lik, y_batch, post_m_batch);
	end

	nlZ=-sum(ll_batch)+0.5*(sum(sum(tmp1.*tmp1))+tmp2'*tmp2)-sum(log(diag(C_u)))+const_term;
