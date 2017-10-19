function nlZ  = batch_nlz_sparsev3(mean, lik, x_batch,  y_batch,  hyp, Ku_batch, m_0, C_u, m_u, Kuu_batch, d0_batch, const_term)
	lik_name = func2str(lik{1});
	alpha=Kuu_batch\(m_u-m_0);

	H_batch=Kuu_batch\Ku_batch;
	m_batch = feval(mean{:}, hyp.mean, x_batch);  
	post_m_batch=m_batch+H_batch'*(m_u-m_0);

	V1_batch=C_u'*H_batch;
	post_v_batch=d0_batch+sum(V1_batch.*V1_batch,1)';

	switch lik_name
	case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
		ll_batch = E_log_p(lik_name, y_batch, post_m_batch, post_v_batch, hyp.lik);
	otherwise
		ll_batch = likKL(post_v_batch, lik, hyp.lik, y_batch, post_m_batch);
	end

	nlZ=-sum(ll_batch)+0.5*(sum(sum( C_u.*(Kuu_batch\C_u)  ))+alpha'*(m_u-m_0))-sum(log(diag(C_u)))+const_term;
