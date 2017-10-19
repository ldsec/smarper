function nlZ  = batch_nlz_sparsev2(mean, lik, x_batch,  y_batch,  hyp, Ku_batch, Kuu_batch, m_0, m_u, d0_batch, D)
	nu=size(m_u,1);
	lik_name = func2str(lik{1});
	alpha=Kuu_batch\(m_u-m_0);
	H_batch=Kuu_batch\Ku_batch;
	m_batch = feval(mean{:}, hyp.mean, x_batch);  
	post_m_batch=m_batch+H_batch'*(m_u-m_0);

	rot180   = @(A)   rot90(rot90(A));                     % little helper functions
	chol_inv = @(A) rot180(chol(rot180(A))')\eye(nu);   

	tmp=Ku_batch*D*Ku_batch';
	V1_batch=chol_inv(Kuu_batch+tmp);
	V1_batch=V1_batch*Ku_batch;
	post_v_batch=d0_batch+sum(V1_batch.*V1_batch,1)';
	 
	C_batch=chol(Kuu_batch,'lower');
	U_batch=chol_inv( eye(nu)+  C_batch\(tmp*(C_batch'\eye(nu))) );


	switch lik_name
	case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
		ll_batch = E_log_p(lik_name, y_batch, post_m_batch, post_v_batch, hyp.lik);
	otherwise
		ll_batch = likKL(post_v_batch, lik, hyp.lik, y_batch, post_m_batch);
	end

	nlZ=-sum(ll_batch)+0.5*(sum( sum( U_batch.*U_batch ) )+alpha'*(m_u-m_0) - nu)-sum( log( diag(U_batch) ) );
